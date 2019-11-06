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
package org.fcrepo.kernel.impl.services;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.graph.NodeFactory.createLiteral;
import static java.util.stream.Stream.of;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.fcrepo.kernel.api.Transaction;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.operations.RdfSourceOperationFactory;
import org.fcrepo.kernel.api.operations.RdfSourceOperationBuilder;
import org.fcrepo.kernel.api.rdf.DefaultRdfStream;
import org.fcrepo.kernel.impl.operations.UpdateRdfSourceOperation;
import org.fcrepo.persistence.api.PersistentStorageSession;
import org.fcrepo.persistence.api.PersistentStorageSessionManager;
import org.fcrepo.persistence.api.exceptions.PersistentStorageException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * DeleteResourceServiceTest
 *
 * @author bseeger
 */
@RunWith(MockitoJUnitRunner.Strict.class)
public class ReplacePropertiesServiceImplTest {

    @Mock
    private Transaction tx;

    @Mock
    private PersistentStorageSession pSession;

    @Mock
    private PersistentStorageSessionManager psManager;

    @Mock
    d
    private RdfSourceOperationFactory factory;

    @Mock
    private RdfSourceOperationBuilder builder;

    @Mock
    private UpdateRdfSourceOperation operation;

    @Mock
    FedoraResource resource;

    @InjectMocks
    private ReplacePropertiesServiceImpl service;

    private final String id = "test-resource";
    private final String txId = "tx-1234";
    private final String contentType = "text/turtle";
    private final String resourceName = "testResource";
    private final Node subject = createURI(resourceName);
    private final Triple triple1 = new Triple(createURI(resourceName),
        createURI("http://purl.org/dc/elements/1.1/title"),
        createLiteral("title one", XSDDatatype.XSDstring));
    private final Triple triple2 = new Triple(createURI(resourceName),
        createURI("http://purl.org/dc/elements/1.1/title"),
        createLiteral("title two", XSDDatatype.XSDstring));

    private final DefaultRdfStream rdfStream = new DefaultRdfStream(subject, of(triple1, triple2));

    private final String rdfString = "@prefix dc: <http://purl.org/dc/elements/1.1/> . \n" +
                    "</path/to/resource1>\n" +
                    "dc:title 'fancy title'\n";

    final InputStream requestBodyStream = new ByteArrayInputStream(rdfString.getBytes());

    @Test
    @Ignore
    public void test() throws PersistentStorageException {

        // TODO finish test
        when(tx.getId()).thenReturn(txId);
        when(psManager.getSession(anyString())).thenReturn(pSession);
        when(resource.getId()).thenReturn(id);
        when(resource.getTriples()).thenReturn(rdfStream);
        when(factory.updateBuilder(eq(id))).thenReturn(builder);
        when(builder.build()).thenReturn(operation);

        service.replaceProperties(tx, resource, requestBodyStream, contentType);
        //verify(pSession).persist(operation);
    }
    @Test(expected = RepositoryRuntimeException.class)
    @Ignore
    public void testException() throws PersistentStorageException {

        // TODO finish test
        when(tx.getId()).thenReturn(txId);
        when(psManager.getSession(anyString())).thenReturn(pSession);
        when(resource.getId()).thenReturn(id);
        when(factory.updateBuilder(eq(id))).thenReturn(builder);
        when(builder.build()).thenReturn(operation);
        doThrow(new PersistentStorageException("error")).when(pSession).persist(eq(operation));
        service.replaceProperties(tx, resource,requestBodyStream, contentType);
    }
}

