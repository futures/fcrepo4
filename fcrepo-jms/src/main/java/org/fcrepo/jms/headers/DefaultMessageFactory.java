/**
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
package org.fcrepo.jms.headers;

import static java.util.Arrays.asList;
import static org.fcrepo.kernel.RdfLexicon.REPOSITORY_NAMESPACE;
import static org.modeshape.jcr.api.JcrConstants.JCR_CONTENT;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.commons.lang.StringUtils;
import org.fcrepo.jms.observer.JMSEventMessageFactory;
import org.fcrepo.kernel.observer.FedoraEvent;
import org.fcrepo.kernel.observer.FixityEvent;
import org.fcrepo.kernel.utils.EventType;
import org.slf4j.Logger;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Generates JMS {@link Message}s composed entirely of headers, based entirely
 * on information found in the {@link FedoraEvent} that triggers publication.
 *
 * @author ajs6f
 * @author escowles
 * @since Dec 2, 2013
 */
public class DefaultMessageFactory implements JMSEventMessageFactory {

    public static final String JMS_NAMESPACE = "org.fcrepo.jms.";

    public static final String TIMESTAMP_HEADER_NAME = JMS_NAMESPACE
            + "timestamp";

    public static final String IDENTIFIER_HEADER_NAME = JMS_NAMESPACE
            + "identifier";

    public static final String EVENT_TYPE_HEADER_NAME = JMS_NAMESPACE
            + "eventType";

    public static final String BASE_URL_HEADER_NAME = JMS_NAMESPACE
            + "baseURL";

    public static final String PROPERTIES_HEADER_NAME = JMS_NAMESPACE
            + "properties";

    public static final String USER_HEADER_NAME = JMS_NAMESPACE + "user";
    public static final String USER_AGENT_HEADER_NAME = JMS_NAMESPACE + "userAgent";

    public static final String FIXITY_HEADER_NAME = JMS_NAMESPACE + "fixity";
    public static final String CONTENT_DIGEST_HEADER_NAME = JMS_NAMESPACE + "contentDigest";
    public static final String CONTENT_SIZE_HEADER_NAME = JMS_NAMESPACE + "contentSize";

    private String baseURL;
    private String userAgent;

    @Override
    public Message getMessage(final FedoraEvent jcrEvent,
        final javax.jms.Session jmsSession) throws RepositoryException,
        JMSException {

        final Message message = jmsSession.createMessage();
        message.setLongProperty(TIMESTAMP_HEADER_NAME, jcrEvent.getDate());
        String path = jcrEvent.getPath();
        if ( path.endsWith("/" + JCR_CONTENT) ) {
            path = path.replaceAll("/" + JCR_CONTENT,"");
        }

        // extract baseURL and userAgent from event UserData
        try {
            final String userdata = jcrEvent.getUserData();
            if (!StringUtils.isBlank(userdata)) {
                final JsonObject json = new JsonParser().parse(userdata).getAsJsonObject();
                String url = json.get("baseURL").getAsString();
                while (url.endsWith("/")) {
                    url = url.substring(0, url.length() - 1);
                }
                this.baseURL = url;
                this.userAgent = json.get("userAgent").getAsString();
                LOGGER.debug("MessageFactory baseURL: {}, userAgent: {}", baseURL, userAgent);

            } else {
                LOGGER.warn("MessageFactory event UserData is empty!");
            }

        } catch ( final RuntimeException ex ) {
            LOGGER.warn("Error setting baseURL or userAgent", ex);
        }

        message.setStringProperty(IDENTIFIER_HEADER_NAME, path);
        message.setStringProperty(EVENT_TYPE_HEADER_NAME, getEventURIs( jcrEvent
                .getTypes()));
        message.setStringProperty(BASE_URL_HEADER_NAME, baseURL);
        message.setStringProperty(USER_HEADER_NAME, jcrEvent.getUserID());
        message.setStringProperty(USER_AGENT_HEADER_NAME, userAgent);
        message.setStringProperty(PROPERTIES_HEADER_NAME, Joiner.on(',').join(jcrEvent.getProperties()));

        LOGGER.trace("getMessage() returning: {}", message);
        return message;
    }

    /**
     *
     * @param fixityEvent
     * @param jmsSession
     * @return
     * @throws RepositoryException
     * @throws JMSException
     */

    @Override
    public Message getFixityMessage(final FixityEvent fixityEvent,
                                    final javax.jms.Session jmsSession) throws RepositoryException,
            JMSException {
        final Message message = jmsSession.createMessage();
        message.setLongProperty(TIMESTAMP_HEADER_NAME, fixityEvent.getDate());
        message.setStringProperty(IDENTIFIER_HEADER_NAME, fixityEvent.getPath());
        message.setStringProperty(EVENT_TYPE_HEADER_NAME, getEventURIs(
                new HashSet<>(asList(fixityEvent.getType()))));
        message.setStringProperty(BASE_URL_HEADER_NAME, fixityEvent.getBaseURL());
        message.setStringProperty(USER_HEADER_NAME, fixityEvent.getUserID());
        message.setStringProperty(USER_AGENT_HEADER_NAME, fixityEvent.getUserData());
        message.setStringProperty(FIXITY_HEADER_NAME, fixityEvent.getFixity());
        message.setStringProperty(CONTENT_DIGEST_HEADER_NAME, fixityEvent.getContentDigest());
        message.setStringProperty(CONTENT_SIZE_HEADER_NAME, fixityEvent.getContentSize());
        LOGGER.trace("getFixityMessage return: {}", message);
        return message;
    }


    private static String getEventURIs(final Set<Integer> types) {
        final String uris = Joiner.on(',').join(Iterables.transform(types, new Function<Integer, String>() {

            @Override
            public String apply(final Integer type) {
                return REPOSITORY_NAMESPACE + EventType.valueOf(type);
            }
        }));
        LOGGER.debug("Constructed event type URIs: {}", uris);
        return uris;
    }

    private static final Logger LOGGER = getLogger(DefaultMessageFactory.class);

}
