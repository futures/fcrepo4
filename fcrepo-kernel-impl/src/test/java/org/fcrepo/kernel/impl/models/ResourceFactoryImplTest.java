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
package org.fcrepo.kernel.impl.models;

import static java.util.Arrays.asList;

import static org.fcrepo.kernel.api.FedoraTypes.FCR_METADATA;
import static org.fcrepo.kernel.api.FedoraTypes.FEDORA_ID_PREFIX;
import static org.fcrepo.kernel.api.RdfLexicon.BASIC_CONTAINER;
import static org.fcrepo.kernel.api.RdfLexicon.DIRECT_CONTAINER;
import static org.fcrepo.kernel.api.RdfLexicon.INDIRECT_CONTAINER;
import static org.fcrepo.kernel.api.RdfLexicon.NON_RDF_SOURCE;
import static org.fcrepo.kernel.api.models.ExternalContent.PROXY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import javax.inject.Inject;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.apache.jena.rdf.model.Resource;
import org.fcrepo.kernel.api.ContainmentIndex;
import org.fcrepo.kernel.api.Transaction;
import org.fcrepo.kernel.api.exception.PathNotFoundException;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.exception.ResourceTypeException;
import org.fcrepo.kernel.api.identifiers.FedoraID;
import org.fcrepo.kernel.api.models.Binary;
import org.fcrepo.kernel.api.models.Container;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.persistence.api.PersistentStorageSession;
import org.fcrepo.persistence.api.PersistentStorageSessionManager;
import org.fcrepo.persistence.api.exceptions.PersistentItemNotFoundException;
import org.fcrepo.persistence.api.exceptions.PersistentSessionClosedException;
import org.fcrepo.persistence.api.exceptions.PersistentStorageException;
import org.fcrepo.persistence.common.ResourceHeadersImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author bbpennel
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/containmentIndexTest.xml")
public class ResourceFactoryImplTest {

    private final static Instant CREATED_DATE = Instant.parse("2019-11-12T10:00:30.0Z");

    private final static String CREATED_BY = "user1";

    private final static Instant LAST_MODIFIED_DATE = Instant.parse("2019-11-12T14:11:05.0Z");

    private final static String LAST_MODIFIED_BY = "user2";

    private final static String STATE_TOKEN = "stately_value";

    private final static long CONTENT_SIZE = 100l;

    private final static String MIME_TYPE = "text/plain";

    private final static String FILENAME = "testfile.txt";

    private final static URI DIGEST = URI.create("sha:12345");

    private final static Collection<URI> DIGESTS = asList(DIGEST);

    @Mock
    private PersistentStorageSessionManager sessionManager;

    @Mock
    private PersistentStorageSession psSession;

    private ResourceHeadersImpl resourceHeaders;

    private String fedoraId;

    private String sessionId;

    private FedoraID rootId = FedoraID.getRoot();

    private FedoraID mockFedoraID;

    private String fedoraMementoId;

    private FedoraID mockFedoraMementoID;

    @Mock
    private Transaction mockTx;

    @Mock
    private FedoraResource mockResource;

    @Inject
    private ContainmentIndex containmentIndex;

    @InjectMocks
    private ResourceFactoryImpl factory;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        fedoraId = FEDORA_ID_PREFIX + UUID.randomUUID().toString();
        mockFedoraID = FedoraID.create(fedoraId);
        fedoraMementoId = fedoraId + "/fcr:versions/20000102120000";
        mockFedoraMementoID = FedoraID.create(fedoraMementoId);

        sessionId = UUID.randomUUID().toString();
        when(mockTx.getId()).thenReturn(sessionId);

        factory = new ResourceFactoryImpl();

        setField(factory, "persistentStorageSessionManager", sessionManager);
        setField(factory, "containmentIndex", containmentIndex);

        resourceHeaders = new ResourceHeadersImpl();
        resourceHeaders.setId(fedoraId);

        when(sessionManager.getSession(sessionId)).thenReturn(psSession);
        when(sessionManager.getReadOnlySession()).thenReturn(psSession);

        when(psSession.getHeaders(eq(fedoraId), nullable(Instant.class))).thenReturn(resourceHeaders);
    }

    @After
    public void cleanUp() {
        when(mockResource.getId()).thenReturn(FEDORA_ID_PREFIX);
        when(mockResource.getFedoraId()).thenReturn(mockFedoraID);
        containmentIndex.rollbackTransaction(mockTx);
        containmentIndex.getContains(null, mockResource).forEach(c ->
                containmentIndex.removeContainedBy(null, rootId, FedoraID.create(c)));
    }

    @Test(expected = PathNotFoundException.class)
    public void getResource_ObjectNotFound() throws Exception {
        when(psSession.getHeaders(fedoraId, null)).thenThrow(PersistentItemNotFoundException.class);

        factory.getResource(mockFedoraID);
    }

    @Test(expected = ResourceTypeException.class)
    public void getResource_NoInteractionModel() throws Exception {
        resourceHeaders.setInteractionModel(null);

        factory.getResource(mockFedoraID);
    }

    @Test(expected = ResourceTypeException.class)
    public void getResource_UnknownInteractionModel() throws Exception {
        resourceHeaders.setInteractionModel("http://example.com/mystery_stroop");

        factory.getResource(mockFedoraID);
    }

    @Test
    public void getResource_BasicContainer() throws Exception {
        populateHeaders(resourceHeaders, BASIC_CONTAINER);

        final var resc = factory.getResource(mockFedoraID);

        assertTrue("Factory must return a container", resc instanceof Container);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);

        verify(sessionManager).getReadOnlySession();
    }

    @Test
    public void getResource_BasicContainer_WithParent() throws Exception {
        populateHeaders(resourceHeaders, BASIC_CONTAINER);

        final var parentId = FEDORA_ID_PREFIX + UUID.randomUUID().toString();
        resourceHeaders.setParent(parentId);

        final var parentHeaders = new ResourceHeadersImpl();
        parentHeaders.setId(parentId);
        populateHeaders(parentHeaders, DIRECT_CONTAINER);

        when(psSession.getHeaders(parentId, null)).thenReturn(parentHeaders);

        final var resc = factory.getResource(mockTx, mockFedoraID);

        assertTrue("Factory must return a container", resc instanceof Container);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);

        final var parentResc = resc.getParent();
        assertTrue("Parent must be a container", parentResc instanceof Container);
        assertEquals(parentId, parentResc.getId());
        assertStateFieldsMatches(parentResc);
    }

    @Test
    public void getResource_BasicContainer_InTransaction() throws Exception {
        populateHeaders(resourceHeaders, BASIC_CONTAINER);

        final var resc = factory.getResource(mockTx, mockFedoraID);

        assertTrue("Factory must return a container", resc instanceof Container);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);

        verify(sessionManager).getSession(sessionId);
    }

    @Test
    public void getResource_BasicContainer_Cast() throws Exception {
        populateHeaders(resourceHeaders, BASIC_CONTAINER);

        final var resc = factory.getResource(mockFedoraID, Container.class);

        assertTrue("Factory must return a container", resc instanceof Container);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);

        verify(sessionManager).getReadOnlySession();
    }

    @Test
    public void getResource_BasicContainer_Cast_InTransaction() throws Exception {
        populateHeaders(resourceHeaders, BASIC_CONTAINER);

        final var resc = factory.getResource(mockTx, mockFedoraID, Container.class);

        assertTrue("Factory must return a container", resc instanceof Container);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);

        verify(sessionManager).getSession(sessionId);
    }

    @Test
    public void getResource_DirectContainer() throws Exception {
        populateHeaders(resourceHeaders, DIRECT_CONTAINER);

        final var resc = factory.getResource(mockFedoraID);

        assertTrue("Factory must return a container", resc instanceof Container);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);
    }

    @Test
    public void getResource_IndirectContainer() throws Exception {
        populateHeaders(resourceHeaders, INDIRECT_CONTAINER);

        final var resc = factory.getResource(mockFedoraID);

        assertTrue("Factory must return a container", resc instanceof Container);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);
    }

    @Test
    public void getResource_Binary() throws Exception {
        populateHeaders(resourceHeaders, NON_RDF_SOURCE);
        populateInternalBinaryHeaders(resourceHeaders);

        final var resc = factory.getResource(mockFedoraID);

        assertTrue("Factory must return a binary", resc instanceof Binary);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);
        assertBinaryFieldsMatch(resc);
    }

    @Test(expected = RepositoryRuntimeException.class)
    public void getResource_Binary_StorageException() throws Exception {
        when(psSession.getHeaders(fedoraId, null)).thenThrow(new PersistentStorageException("Boom"));

        populateHeaders(resourceHeaders, NON_RDF_SOURCE);
        populateInternalBinaryHeaders(resourceHeaders);

        factory.getResource(mockFedoraID);
    }

    @Test
    public void getResource_ExternalBinary() throws Exception {
        populateHeaders(resourceHeaders, NON_RDF_SOURCE);
        populateInternalBinaryHeaders(resourceHeaders);
        final String externalUrl = "http://example.com/stuff";
        resourceHeaders.setExternalUrl(externalUrl);
        resourceHeaders.setExternalHandling(PROXY);

        final var resc = factory.getResource(mockFedoraID);

        assertTrue("Factory must return a container", resc instanceof Binary);
        assertEquals(fedoraId, resc.getId());
        assertStateFieldsMatches(resc);
        assertBinaryFieldsMatch(resc);
        final var binary = (Binary) resc;
        assertEquals(externalUrl, binary.getExternalURL());
        assertTrue(binary.isProxy());
    }

    @Test
    public void doesResourceExist_Exists_WithSession() throws Exception {
        containmentIndex.addContainedBy(mockTx.getId(), rootId, mockFedoraID);
        final boolean answerIn = factory.doesResourceExist(mockTx, mockFedoraID);
        assertTrue(answerIn);
        final boolean answerOut = factory.doesResourceExist(null, mockFedoraID);
        assertFalse(answerOut);
    }

    @Test
    public void doesResourceExist_Exists_Description_WithSession() {
        containmentIndex.addContainedBy(mockTx.getId(), rootId, mockFedoraID);
        final FedoraID descId = mockFedoraID.addToResourceId(FCR_METADATA);
        final boolean answerIn = factory.doesResourceExist(mockTx, descId);
        assertTrue(answerIn);
        final boolean answerOut = factory.doesResourceExist(null, descId);
        assertFalse(answerOut);
    }

    @Test
    public void doesResourceExist_Exists_WithoutSession() throws Exception {
        containmentIndex.addContainedBy(null, rootId, mockFedoraID);
        final boolean answer = factory.doesResourceExist(null, mockFedoraID);
        assertTrue(answer);
    }

    @Test
    public void doesResourceExist_Exists_Description_WithoutSession() {
        containmentIndex.addContainedBy(null, rootId, mockFedoraID);
        final FedoraID descId = mockFedoraID.addToResourceId(FCR_METADATA);
        final boolean answer = factory.doesResourceExist(null, descId);
        assertTrue(answer);
    }

    @Test
    public void doesResourceExist_DoesntExist_WithSession() throws Exception {
        final boolean answer = factory.doesResourceExist(mockTx, mockFedoraID);
        assertFalse(answer);
    }

    @Test
    public void doesResourceExist_DoesntExists_Description_WithSession() {
        final FedoraID descId = mockFedoraID.addToResourceId(FCR_METADATA);
        final boolean answer = factory.doesResourceExist(mockTx, descId);
        assertFalse(answer);
    }

    @Test
    public void doesResourceExist_DoesntExist_WithoutSession() throws Exception {
        final boolean answer = factory.doesResourceExist(null, mockFedoraID);
        assertFalse(answer);
    }

    @Test
    public void doesResourceExist_DoesntExists_Description_WithoutSession() {
        final FedoraID descId = mockFedoraID.addToResourceId(FCR_METADATA);
        final boolean answer = factory.doesResourceExist(null, descId);
        assertFalse(answer);
    }

    /**
     * Only Mementos go to the persistence layer.
     */
    @Test(expected = RepositoryRuntimeException.class)
    public void doesResourceExist_Exception_WithSession() throws Exception {
        when(psSession.getHeaders(mockFedoraMementoID.getResourceId(), mockFedoraMementoID.getMementoInstant()))
                .thenThrow(PersistentSessionClosedException.class);
        factory.doesResourceExist(mockTx, mockFedoraMementoID);
    }

    /**
     * Only Mementos go to the persistence layer.
     */
    @Test(expected = RepositoryRuntimeException.class)
    public void doesResourceExist_Exception_WithoutSession() throws Exception {
        when(psSession.getHeaders(mockFedoraMementoID.getResourceId(), mockFedoraMementoID.getMementoInstant()))
                .thenThrow(PersistentSessionClosedException.class);
        factory.doesResourceExist(null, mockFedoraMementoID);
    }

    private void assertStateFieldsMatches(final FedoraResource resc) {
        assertEquals(CREATED_DATE, resc.getCreatedDate());
        assertEquals(CREATED_BY, resc.getCreatedBy());
        assertEquals(LAST_MODIFIED_DATE, resc.getLastModifiedDate());
        assertEquals(LAST_MODIFIED_BY, resc.getLastModifiedBy());
        assertEquals(STATE_TOKEN, resc.getStateToken());
        assertEquals(STATE_TOKEN, resc.getEtagValue());
    }

    private static void populateHeaders(final ResourceHeadersImpl headers, final Resource ixModel) {
        headers.setInteractionModel(ixModel.getURI());
        headers.setCreatedBy(CREATED_BY);
        headers.setCreatedDate(CREATED_DATE);
        headers.setLastModifiedBy(LAST_MODIFIED_BY);
        headers.setLastModifiedDate(LAST_MODIFIED_DATE);
        headers.setStateToken(STATE_TOKEN);
    }

    private void assertBinaryFieldsMatch(final FedoraResource resc) {
        final Binary binary = (Binary) resc;
        assertEquals(CONTENT_SIZE, binary.getContentSize());
        assertEquals(MIME_TYPE, binary.getMimeType());
        assertEquals(DIGEST, binary.getContentDigest());
        assertEquals(FILENAME, binary.getFilename());
    }

    private static void populateInternalBinaryHeaders(final ResourceHeadersImpl headers) {
        headers.setContentSize(CONTENT_SIZE);
        headers.setMimeType(MIME_TYPE);
        headers.setDigests(DIGESTS);
        headers.setFilename(FILENAME);
    }
}
