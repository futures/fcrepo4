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
package org.fcrepo.kernel.api.utils;

import static org.apache.jena.graph.NodeFactory.createURI;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;

/**
 * Utility for remapping subjects in rdf triples.
 *
 * @author bbpennel
 */
public class SubjectMappingUtil {

    private SubjectMappingUtil() {

    }

    /**
     * @param t
     * @param resourceUri
     * @param destinationUri
     * @return
     */
    public static Triple mapSubject(final Triple t, final String resourceUri, final String destinationUri) {
        final Node destinationNode = createURI(destinationUri);
        return mapSubject(t, resourceUri, destinationNode);
    }

    /**
     * @param t
     * @param resourceUri
     * @param destinationNode
     * @return
     */
    public static Triple mapSubject(final Triple t, final String resourceUri, final Node destinationNode) {
        final Node subject;
        if (t.getSubject().getURI().equals(resourceUri)) {
            subject = destinationNode;
        } else {
            subject = t.getSubject();
        }
        return new Triple(subject, t.getPredicate(), t.getObject());
    }
}