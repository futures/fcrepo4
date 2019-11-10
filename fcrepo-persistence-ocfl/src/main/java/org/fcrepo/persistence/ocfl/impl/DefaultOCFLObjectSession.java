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
package org.fcrepo.persistence.ocfl.impl;

import static edu.wisc.library.ocfl.api.OcflOption.OVERWRITE;
import static edu.wisc.library.ocfl.api.OcflOption.MOVE_SOURCE;
import static org.fcrepo.persistence.ocfl.api.CommitOption.NEW_VERSION;
import static java.lang.String.format;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.fcrepo.persistence.api.exceptions.PersistentItemNotFoundException;
import org.fcrepo.persistence.api.exceptions.PersistentStorageException;
import org.fcrepo.persistence.ocfl.api.CommitOption;
import org.fcrepo.persistence.ocfl.api.OCFLObjectSession;

import edu.wisc.library.ocfl.api.model.ObjectVersionId;
import edu.wisc.library.ocfl.api.MutableOcflRepository;
import edu.wisc.library.ocfl.api.OcflObjectUpdater;
import edu.wisc.library.ocfl.api.exception.NotFoundException;
import edu.wisc.library.ocfl.api.model.CommitInfo;

/**
 * Default implementation of an OCFL object session, which stages changes to the
 * file system prior to committing.
 *
 * @author bbpennel
 */
public class DefaultOCFLObjectSession implements OCFLObjectSession {

    private String objectIdentifier;

    // Path where changes to the OCFL object in this session are staged
    private Path stagingPath;

    private Set<String> deletePaths;

    private boolean objectDeleted;

    private MutableOcflRepository ocflRepository;

    /**
     * Instantiate an OCFL object session
     *
     * @param objectIdentifier identifier for the OCFL object
     * @param stagingPath path in which changes to the OCFL object will be staged.
     * @param ocflRepository the OCFL repository in which the object is stored.
     */
    public DefaultOCFLObjectSession(final String objectIdentifier, final Path stagingPath,
            final MutableOcflRepository ocflRepository) {
        this.objectIdentifier = objectIdentifier;
        this.stagingPath = stagingPath;
        this.ocflRepository = ocflRepository;
        this.deletePaths = new HashSet<>();
        this.objectDeleted = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final String subpath, final InputStream stream) throws PersistentStorageException {
        // Determine the staging path for the incoming content
        final var stagedPath = resolveStagedPath(subpath);
        final var parentPath = stagedPath.getParent();

        try {
            // Fill in any missing parent directories
            Files.createDirectories(parentPath);
            // write contents to subpath within the staging path
            Files.copy(stream, stagedPath, StandardCopyOption.REPLACE_EXISTING);
            stream.close();
        } catch (final IOException e) {
            throw new PersistentStorageException("Unable to persist content to " + stagedPath, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * If the file was not newly created within this session, then the
     * deletion will be recorded for replay at commit time.
     */
    @Override
    public void delete(final String subpath) throws PersistentStorageException {
        final var stagedPath = resolveStagedPath(subpath);
        final var hasStagedChanges = hasStagedChanges(stagedPath);

        // If the subpath exists in the staging path for this session, then delete from there
        if (hasStagedChanges) {
            // delete the file from the staging path
            try {
                Files.delete(stagedPath);
            } catch (final IOException e) {
                throw new PersistentStorageException("Unable to delete " + stagedPath, e);
            }
        }

        // for file that existed before this session, queue up its deletion for commit time
        if (!newInSession(subpath)) {
            deletePaths.add(subpath);
        } else if (!hasStagedChanges) {
            // File is neither in the staged or exists in the head version, so file cannot be found for deletion
            if (objectDeleted && isStagingEmpty()) {
                // File can't be found because the object no longer exists
                throw new PersistentItemNotFoundException(format(
                        "Could not delete from object %s, it does not exist", objectIdentifier));
            } else {
                throw new PersistentItemNotFoundException(format("Could not find %s within object %s",
                        subpath, objectIdentifier));
            }
        }
    }

    @Override
    public void deleteObject() throws PersistentStorageException {
        objectDeleted = true;
        // Reset state of the object
        if (!isStagingEmpty()) {
            try {
                FileUtils.cleanDirectory(stagingPath.toFile());
            } catch (final IOException e) {
                throw new PersistentStorageException("Unable to cleanup staging path", e);
            }
            deletePaths = new HashSet<>();
        } else if (!ocflRepository.containsObject(objectIdentifier)) {
            // fail if attempting to delete object that does not exist and has no staged changes
            throw new PersistentItemNotFoundException(format(
                    "Cannot delete object %s, it does not exist", objectIdentifier));
        }
    }

    private boolean newInSession(final String subpath) {
        // If the object was deleted in this session, then content can only be new
        if (objectDeleted) {
            return true;
        }
        // If the object isn't created yet, then there is no history for the subpath
        if (!ocflRepository.containsObject(objectIdentifier)) {
            return true;
        }
        // determine if this subpath exists in the OCFL object
        return !ocflRepository.getObject(ObjectVersionId.head(objectIdentifier))
                .containsFile(subpath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream read(final String subpath) throws PersistentItemNotFoundException {
        final var stagedPath = resolveStagedPath(subpath);

        if (hasStagedChanges(stagedPath)) {
            // prioritize read of the staged version of the file
            try {
                return new FileInputStream(stagedPath.toFile());
            } catch (final FileNotFoundException e) {
                throw new PersistentItemNotFoundException(format("Could not find %s within object %s",
                        subpath, objectIdentifier));
            }
        } else if (!objectDeleted) {
            // Fall back to the head version
            return readVersion(subpath, ObjectVersionId.head(objectIdentifier));
        }

        throw new PersistentItemNotFoundException(format("Could not find %s within object %s",
                subpath, objectIdentifier));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream read(final String subpath, final String version) throws PersistentItemNotFoundException {
        // If the object was deleted, then only uncommitted staged files can be available
        if (objectDeleted) {
            throw new PersistentItemNotFoundException(format("Could not find %s within object %s",
                    subpath, objectIdentifier));
        }

        return readVersion(subpath, ObjectVersionId.version(objectIdentifier, version));
    }

    private InputStream readVersion(final String subpath, final ObjectVersionId version) throws PersistentItemNotFoundException {
        try {
            // read the head version of the file from the ocfl object
            final var file = ocflRepository.getObject(version)
                    .getFile(subpath);
            if (file == null) {
                throw new PersistentItemNotFoundException(format("Could not find %s within object %s version %s",
                        subpath, objectIdentifier, version.getVersionId()));
            }
            return file.getStream();
        } catch (final NotFoundException e) {
            throw new PersistentItemNotFoundException(format(
                    "Unable to read %s from object %s version %s, object was not found.",
                    subpath, objectIdentifier, version.getVersionId()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare() {
        // TODO check for conflicts and lock the object
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String commit(final CommitOption commitOption) throws PersistentStorageException {
        if (commitOption == null) {
            throw new IllegalArgumentException("Invalid commit option provided: " + commitOption);
        }
        // Perform requested deletion of the object
        if (objectDeleted) {
            deleteExistingObject();
            // no new state provided for the object, this is just committing the delete
            if (isStagingEmpty()) {
                return null;
            }
            // new state exists, object is being recreated after delete
        }

        // Determine if a new object needs to be created
        if (isNewObject()) {
            return commitNewObject(commitOption);
        } else {
            return commitUpdates(commitOption);
        }
    }

    private void deleteExistingObject() throws PersistentStorageException {
        ocflRepository.purgeObject(objectIdentifier);
    }

    private String commitNewObject(final CommitOption commitOption) throws PersistentStorageException {
        final var commitInfo = new CommitInfo().setMessage("initial commit");

        // Prevent creation of empty OCFL objects
        if (isStagingEmpty()) {
            throw new PersistentStorageException("Cannot create empty OCFL object " + objectIdentifier);
        }

        if (NEW_VERSION.equals(commitOption)) {
            // perform commit to new version
            return ocflRepository.putObject(ObjectVersionId.head(objectIdentifier),
                    stagingPath,
                    commitInfo,
                    MOVE_SOURCE)
                    .getVersionId()
                    .toString();
        } else {
            // perform commit to head version
            return ocflRepository.stageChanges(ObjectVersionId.head(objectIdentifier), commitInfo, updater -> {
                updater.addPath(stagingPath, "", MOVE_SOURCE);
            }).getVersionId().toString();
        }
    }

    private String commitUpdates(final CommitOption commitOption) {
        // Updater which pushes all updated files and then performs queued deletes
        final Consumer<OcflObjectUpdater> commitChangeUpdater = updater -> {
            if (!isStagingEmpty()) {
                updater.addPath(stagingPath, "", MOVE_SOURCE, OVERWRITE);
            }
            deletePaths.forEach(updater::removeFile);
        };

        if (NEW_VERSION.equals(commitOption)) {
            // check if a mutable head exists for this object
            if (ocflRepository.hasStagedChanges(objectIdentifier)) {
                // Persist the current changes to the mutable head, and then commit the head as a version
                ocflRepository.stageChanges(ObjectVersionId.head(objectIdentifier),
                        new CommitInfo(),
                        commitChangeUpdater);
                return ocflRepository.commitStagedChanges(objectIdentifier, new CommitInfo())
                        .getVersionId().toString();
            } else {
                // Commit directly to a new version
                return ocflRepository.updateObject(ObjectVersionId.head(objectIdentifier),
                        new CommitInfo(),
                        commitChangeUpdater)
                        .getVersionId().toString();
            }
        } else {
            // perform commit to mutable head version
            return ocflRepository.stageChanges(ObjectVersionId.head(objectIdentifier),
                    new CommitInfo(),
                    commitChangeUpdater)
                    .getVersionId().toString();
        }
    }

    private boolean isStagingEmpty() {
        return stagingPath.toFile().listFiles().length == 0;
    }

    private boolean hasStagedChanges(final Path path) {
        return path.toFile().exists();
    }

    private boolean isNewObject() {
        return !ocflRepository.containsObject(objectIdentifier);
    }

    private Path resolveStagedPath(final String subpath) {
        return stagingPath.resolve(subpath);
    }
}