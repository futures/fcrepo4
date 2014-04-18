/**
 * Copyright 2013 DuraSpace, Inc.
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

package org.fcrepo.kernel.services;

import org.fcrepo.kernel.Lock;
import org.springframework.stereotype.Component;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.lock.LockManager;

/**
 * @author Mike Durbin
 */
@Component
public class LockServiceImpl extends AbstractService implements LockService  {

    @Override
    public Lock acquireLock(final Session session, final String path, long timeout, boolean deep)
        throws RepositoryException {
        final LockManager lockManager = session.getWorkspace().getLockManager();
        final Lock lock = new JCRLock(lockManager.lock(path, deep, false, timeout, session.getUserID()));
        return lock;
    }

    @Override
    public Lock getLock(final Session session, final String path) throws RepositoryException {
        final LockManager lockManager = session.getWorkspace().getLockManager();
        if (!lockManager.isLocked(path)) {
            throw new PathNotFoundException("No lock at path " + path + "!");
        }
        return new JCRLock(lockManager.getLock(path));
    }

    @Override
    public void releaseLock(final Session session, final String path) throws RepositoryException {
        final LockManager lockManager = session.getWorkspace().getLockManager();
        if (!lockManager.isLocked(path)) {
            throw new PathNotFoundException("No lock at path " + path + "!");
        }
        if (!lockManager.getLock(path).isLockOwningSession()) {
            throw new LockException("Lock is not held by this session!");
        }
        session.getWorkspace().getLockManager().unlock(path);
    }

    private static class JCRLock implements Lock {

        private boolean isDeep;

        private String token;

        public JCRLock(final javax.jcr.lock.Lock lock) {
            isDeep = lock.isDeep();
            token = lock.getLockToken();
        }

        @Override
        public String getLockToken() {
            return token;
        }

        @Override
        public boolean isDeep() {
            return isDeep;
        }

    }

}
