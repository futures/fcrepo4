/*
 * Copyright 2015 DuraSpace, Inc.
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
package org.fcrepo.http.api.responses;

import org.springframework.stereotype.Component;

import javax.ws.rs.Produces;

import static javax.ws.rs.core.MediaType.APPLICATION_XHTML_XML;
import static javax.ws.rs.core.MediaType.TEXT_HTML;

/**
 * JAX-RS provider for taking an RdfStream and returning some nice looking
 * HTML
 *
 * @author ajs6f
 */
@Component
@Produces({TEXT_HTML, APPLICATION_XHTML_XML})
public class StreamingHtmlProvider extends StreamingBaseHtmlProvider{
}
