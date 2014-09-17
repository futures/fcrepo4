/**
 * Copyright 2014 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.kernel.impl.services;

import static org.fcrepo.kernel.impl.utils.FedoraTypesUtils.getVersionHistory;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;

import org.fcrepo.kernel.FedoraResource;
import org.fcrepo.kernel.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.impl.DatastreamImpl;
import org.fcrepo.kernel.impl.FedoraResourceImpl;
import org.fcrepo.kernel.impl.rdf.impl.NodeTypeRdfContext;
import org.fcrepo.kernel.services.NodeService;
import org.fcrepo.kernel.utils.iterators.PropertyIterator;
import org.fcrepo.kernel.utils.iterators.RdfStream;
import org.modeshape.jcr.api.nodetype.NodeTypeManager;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Service for managing access to Fedora 'nodes' (either datastreams or objects,
 * we don't care.)
 *
 * @author Chris Beer
 * @since May 9, 2013
 */
@Component
public class NodeServiceImpl extends AbstractService implements NodeService {

    private static final Logger LOGGER = getLogger(NodeServiceImpl.class);

    /**
     * Find or create a new Fedora resource at the given path
     *
     * @param session a JCR session
     * @param path a JCR path
     * @return FedoraResource
     * @throws RepositoryException
     */
    @Override
    public FedoraResource findOrCreateObject(final Session session, final String path) {
        try {
            return new FedoraResourceImpl(findOrCreateNode(session, path));
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Retrieve an existing Fedora resource at the given path
     *
     * @param session a JCR session
     * @param path a JCR path
     * @return Fedora resource at the given path
     * @throws RepositoryException
     */
    @Override
    public FedoraResource getObject(final Session session, final String path) {
        try {
            final Node node = session.getNode(path);

            if (node.isNodeType("nt:file")) {
                return new DatastreamImpl(node);
            } else {
                return new FedoraResourceImpl(node);
            }
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Get an existing Fedora resource at the given path with the given version
     * label
     *
     * @param session a JCR session
     * @param path a JCR path
     * @param versionId a JCR version label
     * @return Fedora resource at the given path with the given version label
     * @throws RepositoryException
     */
    @Override
    public FedoraResource getObject(final Session session, final String path, final String versionId) {
        try {
            final VersionHistory versionHistory = getVersionHistory(session, path);

            if (versionHistory == null) {
                return null;
            }

            if (!versionHistory.hasVersionLabel(versionId)) {
                return null;
            }

            final Version version = versionHistory.getVersionByLabel(versionId);
            return new FedoraResourceImpl(version.getFrozenNode());
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Delete an existing object from the repository at the given path
     *
     * @param session
     * @param path
     * @throws RepositoryException
     */
    @Override
    public void deleteObject(final Session session, final String path) {
        try {
            final Node obj = session.getNode(path);
            final PropertyIterator inboundProperties = new PropertyIterator(obj.getReferences());

            for (final Property inboundProperty : inboundProperties) {
                inboundProperty.remove();
            }

            obj.remove();
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Copy an existing object from the source path to the destination path
     *
     * @param session
     * @param source
     * @param destination
     * @throws RepositoryException
     */
    @Override
    public void copyObject(final Session session, final String source, final String destination) {
        try {
            session.getWorkspace().copy(source, destination);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Move an existing object from the source path to the destination path
     *
     * @param session
     * @param source
     * @param destination
     * @throws RepositoryException
     */
    @Override
    public void moveObject(final Session session, final String source, final String destination) {
        try {
            session.getWorkspace().move(source, destination);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Get the full list of node types in the repository
     *
     * @param session
     * @return full list of node types in the repository
     * @throws RepositoryException
     */
    @Override
    public NodeTypeIterator getAllNodeTypes(final Session session) {
        try {
            final NodeTypeManager ntmanager = (NodeTypeManager) session.getWorkspace().getNodeTypeManager();
            return ntmanager.getAllNodeTypes();
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * @param session
     * @return node types
     * @throws RepositoryException
     */
    @Override
    public RdfStream getNodeTypes(final Session session) {
        try {
            return new NodeTypeRdfContext(session.getWorkspace().getNodeTypeManager());
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * @param session
     * @param cndStream
     * @throws RepositoryException
     * @throws IOException
     */
    @Override
    public void registerNodeTypes(final Session session, final InputStream cndStream) throws IOException {
        try {
            final NodeTypeManager nodeTypeManager = (NodeTypeManager) session.getWorkspace().getNodeTypeManager();
            nodeTypeManager.registerNodeTypes(cndStream, true);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }
}
