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
package org.fcrepo.kernel.modeshape;

import org.apache.jena.rdf.model.Resource;
import org.fcrepo.kernel.api.exception.InvalidChecksumException;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.exception.UnsupportedAccessTypeException;
import org.fcrepo.kernel.api.exception.UnsupportedAlgorithmException;
import org.fcrepo.kernel.api.identifiers.IdentifierConverter;
import org.fcrepo.kernel.api.models.FedoraBinary;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.services.policy.StoragePolicyDecisionPoint;
import org.fcrepo.kernel.api.RdfStream;
import org.fcrepo.kernel.api.TripleCategory;
import org.fcrepo.kernel.api.utils.CacheEntry;
import org.fcrepo.kernel.api.utils.ContentDigest;
import org.fcrepo.kernel.api.utils.FixityResult;
import org.fcrepo.kernel.modeshape.rdf.impl.FixityRdfContext;
import org.fcrepo.kernel.modeshape.utils.FedoraTypesUtils;
import org.fcrepo.kernel.modeshape.utils.impl.CacheEntryFactory;
import org.fcrepo.metrics.RegistryService;
import org.modeshape.jcr.api.Binary;
import org.modeshape.jcr.api.ValueFactory;
import org.fcrepo.kernel.api.utils.MessageExternalBodyContentType;
import org.slf4j.Logger;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.jena.datatypes.xsd.XSDDatatype.XSDstring;
import static org.fcrepo.kernel.api.RdfLexicon.FEDORA_DESCRIPTION;
import static org.fcrepo.kernel.api.utils.ContentDigest.DIGEST_ALGORITHM.SHA1;
import static org.fcrepo.kernel.api.utils.MessageExternalBodyContentType.isExternalBodyType;
import static org.fcrepo.kernel.modeshape.FedoraJcrConstants.FIELD_DELIMITER;
import static org.fcrepo.kernel.modeshape.utils.FedoraTypesUtils.isFedoraBinary;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author cabeer
 * @since 9/19/14
 */
public class FedoraBinaryImpl extends FedoraResourceImpl implements FedoraBinary {

    private static final Logger LOGGER = getLogger(FedoraBinaryImpl.class);

    private static final String LOCAL_FILE_ACCESS_TYPE = "file";

    private static final String URL_ACCESS_TYPE = "http";

    private FedoraBinary wrappedBinary;

    /**
     * Wrap an existing Node as a Fedora Binary
     * @param node the node
     */
    public FedoraBinaryImpl(final Node node) {
        super(node);
    }

    /**
     * Get the proxied binary content object wrapped by this object
     *
     * @return the fedora binary
     */
    private FedoraBinary getBinary() {
        if (wrappedBinary == null) {
            wrappedBinary = getBinaryImplementation();
            LOGGER.debug("Wrapping binary content of type {}", wrappedBinary.getClass().getName());
        }
        return wrappedBinary;
    }
/*
    private String getEffectiveMimeType(final String contentType) {
        try {

            if (contentType != null) {
                return contentType;
            }

            if (hasProperty(HAS_MIME_TYPE)) {
                return getProperty(HAS_MIME_TYPE).getString().replace(FIELD_DELIMITER + XSDstring.getURI(), "");
            }

            return "application/octet-stream";
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }
*/
    private FedoraBinary getBinaryImplementation() {
        final String url = getURLInfo();
        LOGGER.debug("getBinaryImplementation: url is '{}'",url);
        if (url != null) {
            if (url.toLowerCase().startsWith(LOCAL_FILE_ACCESS_TYPE)) {
                LOGGER.debug("Instantiating local file FedoraBinary");
                return new LocalFileBinary(getNode());
            } else if (url.toLowerCase().startsWith(URL_ACCESS_TYPE)) {
                LOGGER.debug("Instantiating URI FedoraBinary");
                return new UrlBinary(getNode());
            }
        }

        LOGGER.debug("Instantiating Internal Fedora Binary");
        return new InternalFedoraBinary(getNode());
    }

    private String getURLInfo() {
        if (isProxy()) {
            return getProxyURL();
        } else if (isRedirect()) {
            return getRedirectURL();
        }

        return null;
    }

    @Override
    public FedoraResource getDescription() {
        return getBinary().getDescription();
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#getContent()
     */
    @Override
    public InputStream getContent() {
        return getBinary().getContent();
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#setContent(java.io.InputStream,
     * java.lang.String, java.net.URI, java.lang.String,
     * org.fcrepo.kernel.api.services.policy.StoragePolicyDecisionPoint)
     */
    @Override
    public void setContent(final InputStream content, final String contentType,
                           final Collection<URI> checksums, final String originalFileName,
                           final StoragePolicyDecisionPoint storagePolicyDecisionPoint)
            throws InvalidChecksumException {

        // Clear the wrapped binary object prior to setting the content
        wrappedBinary = null;
/*
        if (isCopy()) {
            try {
                final MessageExternalBodyContentType parsedContentType = MessageExternalBodyContentType.parse(
                        contentType);

                // Expiration of external-body specified, pull content in
                final String expiration = parsedContentType.getExpiration();
                if (expiration != null && !expiration.isEmpty()) {
                    String dsMimeType = parsedContentType.getMimeType();
                    if (dsMimeType == null || dsMimeType.isEmpty()) {
                        dsMimeType = "application/octet-stream";
                    }

                    final String storageLocation = parsedContentType.getResourceLocation();
                    final InputStream externalStream = new URL(storageLocation).openStream();

                    getBinary(dsMimeType).setContent(externalStream, dsMimeType, checksums, originalFileName,
                            storagePolicyDecisionPoint);
                    return;
                }
            } catch (final UnsupportedAccessTypeException e) {
                throw new RepositoryRuntimeException(e);
            } catch (final IOException e) {
                throw new RepositoryRuntimeException(e);
            }
        }
        */

        getBinary().setContent(content, contentType, checksums, originalFileName,
                storagePolicyDecisionPoint);
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#getContentSize()
     */
    @Override
    public long getContentSize() {
        return getBinary().getContentSize();
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#getContentDigest()
     */
    @Override
    public URI getContentDigest() {
        return getBinary().getContentDigest();
    }


    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#isProxy()
     */
    @Override
    public Boolean isProxy() {
        return hasProperty(PROXY_FOR);
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#isRedirect()
     */
    @Override
    public Boolean isRedirect() {
        return hasProperty(REDIRECTS_TO);
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#getProxyURL()
     */
    @Override
    public String getProxyURL() {
        try {
            if (hasProperty(PROXY_FOR)) {
                return getProperty(PROXY_FOR).getString();
            }
            return null;
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#setProxyURL()
     */
    @Override
    public void setProxyURL(final String url) throws RepositoryRuntimeException {
        try {
            getNode().setProperty(PROXY_FOR, url);
            getNode().setProperty(REDIRECTS_TO, (Value)null);
        } catch (Exception e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#getRedirectURL()
     */
    @Override
    public String getRedirectURL() {
        try {
            if (hasProperty(REDIRECTS_TO)) {
                return getProperty(REDIRECTS_TO).getString();
            }
            return null;
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#setRedirectURL()
     */
    @Override
    public void setRedirectURL(final String url) throws RepositoryRuntimeException {
        try {
            getNode().setProperty(REDIRECTS_TO, url);
            getNode().setProperty(PROXY_FOR, (Value)null);
        } catch (Exception e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#getMimeType()
     */
    @Override
    public String getMimeType() {
        return getBinary().getMimeType();
    }


    /*
     * (non-Javadoc)
     * @see org.fcrepo.kernel.api.models.FedoraBinary#getFilename()
     */
    @Override
    public String getFilename() {
        return getBinary().getFilename();
    }

    @Override
    public RdfStream getFixity(final IdentifierConverter<Resource, FedoraResource> idTranslator) {
        return getBinary().getFixity(idTranslator);
    }

    @Override
    public RdfStream getFixity(final IdentifierConverter<Resource, FedoraResource> idTranslator,
                               final URI digestUri,
                               final long size) {

        return getBinary().getFixity(idTranslator, digestUri, size);
    }

    @Override
    public Collection<URI> checkFixity( final IdentifierConverter<Resource, FedoraResource> idTranslator,
                                        final Collection<String> algorithms)
                                            throws UnsupportedAlgorithmException, UnsupportedAccessTypeException {

        return getBinary().checkFixity(idTranslator, algorithms);
    }

    /**
     * When deleting the binary, we also need to clean up the description document.
     */
    @Override
    public void delete() {
        getBinary().delete();
    }

    @Override
    public FedoraResource getBaseVersion() {
        return getBinary().getBaseVersion();
    }

    private boolean hasDescriptionProperty(final String relPath) {
        try {
            final Node descNode = getDescriptionNodeOrNull();
            if (descNode == null) {
                return false;
            }
            return descNode.hasProperty(relPath);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    private Property getDescriptionProperty(final String relPath) {
        try {
            return getDescriptionNode().getProperty(relPath);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    @Override
    public boolean isVersioned() {
        return getBinary().isVersioned();
    }

    @Override
    public void enableVersioning() {
        getBinary().enableVersioning();
    }

    @Override
    public void disableVersioning() {
        getBinary().disableVersioning();
    }

    /**
     * Check if the given node is a Fedora binary
     * @param node the given node
     * @return whether the given node is a Fedora binary
     */
    public static boolean hasMixin(final Node node) {
        return isFedoraBinary.test(node);
    }
}
