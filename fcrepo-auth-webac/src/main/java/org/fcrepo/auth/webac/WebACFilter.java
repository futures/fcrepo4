/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.auth.webac;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.apache.jena.riot.WebContent.contentTypeSPARQLUpdate;
import static org.fcrepo.auth.common.ServletContainerAuthFilter.FEDORA_ADMIN_ROLE;
import static org.fcrepo.auth.common.ServletContainerAuthFilter.FEDORA_USER_ROLE;
import static org.fcrepo.auth.webac.URIConstants.WEBAC_MODE_APPEND;
import static org.fcrepo.auth.webac.URIConstants.WEBAC_MODE_READ;
import static org.fcrepo.auth.webac.URIConstants.WEBAC_MODE_WRITE;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.URI;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.sparql.modify.request.UpdateModify;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.fcrepo.http.commons.session.SessionFactory;
import org.fcrepo.kernel.api.FedoraSession;
import org.fcrepo.kernel.api.models.FedoraBinary;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.services.NodeService;
import org.slf4j.Logger;

/**
 * @author peichman
 */
public class WebACFilter implements Filter {

    private static final Logger log = getLogger(WebACFilter.class);

    private FedoraResource resource;

    private FedoraSession session;

    @Inject
    private NodeService nodeService;

    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        // this method intentionally left empty
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final Subject currentUser = SecurityUtils.getSubject();
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (isSparqlUpdate(httpRequest)) {
            httpRequest = new CachedSparqlRequest(httpRequest);
        }

        if (currentUser.isAuthenticated()) {
            log.debug("User is authenticated");
            if (currentUser.hasRole(FEDORA_ADMIN_ROLE)) {
                log.debug("User has fedoraAdmin role");
            } else if (currentUser.hasRole(FEDORA_USER_ROLE)) {
                log.debug("User has fedoraUser role");
                // non-admins are subject to permission checks
                if (!isAuthorized(currentUser, httpRequest)) {
                    // if the user is not authorized, set response to forbidden
                    ((HttpServletResponse) response).sendError(SC_FORBIDDEN);
                    return;
                }
            } else {
                log.debug("User has no recognized servlet container role");
                // missing a container role, return forbidden
                ((HttpServletResponse) response).sendError(SC_FORBIDDEN);
                return;
            }
        } else {
            log.debug("User is NOT authenticated");
            // anonymous users are subject to permission checks
            if (!isAuthorized(currentUser, httpRequest)) {
                // if anonymous user is not authorized, set response to forbidden
                ((HttpServletResponse) response).sendError(SC_FORBIDDEN);
                return;
            }
        }

        // proceed to the next filter
        chain.doFilter(httpRequest, response);
    }

    @Override
    public void destroy() {
        // this method intentionally left empty
    }

    private FedoraSession session() {
        if (session == null) {
            session = sessionFactory.getInternalSession();
        }
        return session;
    }

    private FedoraResource resource(final String repoPath) {
        if (resource == null) {
            resource = nodeService.find(session(), repoPath);
        }
        return resource;
    }

    private String findNearestParent(final String childPath) {
        log.debug("Checking child path {}", childPath);

        if (childPath.isEmpty()) {
            return "/";
        }
        final String parentPath = childPath.substring(0, childPath.lastIndexOf("/"));
        log.debug("Checking parent path {}", parentPath);
        if (nodeService.exists(session(), parentPath)) {
            return parentPath;
        } else {
            return findNearestParent(parentPath);
        }
    }

    private boolean isAuthorized(final Subject currentUser, final HttpServletRequest httpRequest) throws IOException {
        final String requestURL = httpRequest.getRequestURL().toString();
        final String repoPath = httpRequest.getPathInfo();

        final URI requestURI = URI.create(requestURL);
        log.debug("Request URI is {}", requestURI);

        // WebAC permissions
        final WebACPermission toRead = new WebACPermission(WEBAC_MODE_READ, requestURI);
        final WebACPermission toWrite = new WebACPermission(WEBAC_MODE_WRITE, requestURI);
        final WebACPermission toAppend = new WebACPermission(WEBAC_MODE_APPEND, requestURI);

        switch (httpRequest.getMethod()) {
        case "GET":
            return currentUser.isPermitted(toRead);
        case "PUT":
            if (currentUser.isPermitted(toWrite)) {
                return true;
            } else {
                if (nodeService.exists(session(), repoPath)) {
                    // can't PUT to an existing resource without acl:Write permission
                    return false;
                } else {
                    // find nearest parent resource and verify that user has acl:Append on it
                    final String baseURL = requestURL.replace(repoPath, "");
                    final String nearestParent = findNearestParent(repoPath);
                    log.debug("Nearest parent path of the new resource is {}", nearestParent);
                    final WebACPermission toAppendToParentURI = new WebACPermission(WEBAC_MODE_APPEND, URI.create(
                            baseURL + nearestParent));
                    return currentUser.isPermitted(toAppendToParentURI);
                }
            }
        case "POST":
            if (currentUser.isPermitted(toWrite)) {
                return true;
            } else {
                if (resource(repoPath) instanceof FedoraBinary) {
                    // LDP-NR
                    // user without the acl:Write permission cannot POST to binaries
                    return false;
                } else {
                    // LDP-RS
                    // user with the acl:Append permission may POST to containers
                    return currentUser.isPermitted(toAppend);
                }
            }
        case "DELETE":
            return currentUser.isPermitted(toWrite);
        case "PATCH":
            if (currentUser.isPermitted(toWrite)) {
                return true;
            } else {
                if (currentUser.isPermitted(toAppend)) {
                    return isPatchContentPermitted(httpRequest);
                }
            }
            return false;
        default:
            return false;
        }
    }

    private boolean isPatchContentPermitted(final HttpServletRequest httpRequest) throws IOException {
        if (!isSparqlUpdate(httpRequest)) {
            log.debug("Cannot verify authorization on NON-SPARQL Patch request.");
            return false;
        }
        if (httpRequest.getInputStream() != null) {
            boolean noDeletes = false;
            try {
                noDeletes = !hasDeleteClause(IOUtils.toString(httpRequest.getInputStream(), UTF_8));
            } catch (QueryParseException ex) {
                log.error("Cannot verify authorization! Exception while inspecting SPARQL query!", ex);
            }
            return noDeletes;
        } else {
            log.debug("Authorizing SPARQL request with no content.");
            return true;
        }
    }

    private boolean hasDeleteClause(final String sparqlString) {
        final UpdateRequest sparqlUpdate = UpdateFactory.create(sparqlString);
        return sparqlUpdate.getOperations().stream().filter(update -> (update instanceof UpdateModify))
                .peek(update -> log.debug("Inspecting update statement for DELETE clause: {}", update.toString()))
                .map(update -> (UpdateModify)update)
                .anyMatch(UpdateModify::hasDeleteClause);
    }

    private boolean isSparqlUpdate(final HttpServletRequest request) {
        return request.getMethod().equals("PATCH") &&
                contentTypeSPARQLUpdate.equalsIgnoreCase(request.getContentType());
    }
}
