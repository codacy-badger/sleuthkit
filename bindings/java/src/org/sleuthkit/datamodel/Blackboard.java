/*
 * Sleuth Kit Data Model
 *
 * Copyright 2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.datamodel;

import com.google.common.collect.ImmutableSet;
import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import org.sleuthkit.datamodel.BlackboardArtifact.Type;

/**
 * A representation of the blackboard, a place where artifacts and their
 * attributes are posted.
 *
 */
public final class Blackboard implements Closeable {

	private SleuthkitCase caseDb;

	/**
	 * Constructs a representation of the blackboard, a place where artifacts
	 * and their attributes are posted.
	 *
	 * @param casedb The case database.
	 */
	Blackboard(SleuthkitCase casedb) {
		this.caseDb = casedb;
	}

	/**
	 * Posts the artifact. The artifact should be complete (all attributes have
	 * been added) before being posted. Posting the artifact includes making any
	 * events that may be derived from it, and broadcasting a notification that
	 * the artifact is ready for further analysis.
	 *
	 * @param moduleName The name of the module posting this artifact.
	 * @param artifact   The artifact to be posted.
	 *
	 * @throws BlackboardException If there is a problem posting the artifact.
	 */
	public synchronized void postArtifact(String moduleName, BlackboardArtifact artifact) throws BlackboardException {
		if (null == caseDb) {
			throw new BlackboardException("Blackboard has been closed");
		}
		addEventsFromArtifacts(artifact);

		try {
			caseDb.fireTSKEvent(new ArtifactsPostedEvent(moduleName, artifact));
		} catch (TskCoreException ex) {
			throw new BlackboardException("Error firing ArtifactPostedEvent", ex);
		}

	}

	private synchronized void addEventsFromArtifacts(BlackboardArtifact artifact) throws BlackboardException {
		try {
			caseDb.getTimelineManager().addEventsFromArtifact(artifact);
		} catch (TskCoreException ex) {
			throw new BlackboardException("Failed to add events for artifact: " + artifact, ex);
		}
	}

	/**
	 * Posts a Collection of artifacts. The artifacts should be complete (all
	 * attributes have been added) before being posted. Posting the artifacts
	 * includes making any events that may be derived from them, and
	 * broadcasting a notification that the artifacts are ready for further
	 * analysis.
	 *
	 * @param moduleName   The name of the module posting these artifacts.
	 * @param artifactType The type of artifacts that are being posted. This
	 *                     should match the artifacts, but is not checked.
	 * @param artifacts    The artifacts to be posted.
	 *
	 * @throws BlackboardException If there is a problem posting the artifacts.
	 */
	public synchronized void postArtifacts(String moduleName, Type artifactType, Collection<BlackboardArtifact> artifacts) throws BlackboardException {
		if (null == caseDb) {
			throw new BlackboardException("Blackboard has been closed");
		}
		/*
		 * For now this just processes them one by one, but in the future it
		 * could be smarter and use transactions, etc.
		 */
		for (BlackboardArtifact artifact : artifacts) {
			addEventsFromArtifacts(artifact);
		}

		caseDb.fireTSKEvent(new ArtifactsPostedEvent(moduleName, artifactType, artifacts));
	}

	public synchronized void postArtifacts(String moduleName, BlackboardArtifact.ARTIFACT_TYPE artifactType, Collection<BlackboardArtifact> artifacts) throws BlackboardException {
		this.postArtifacts(moduleName, new Type(artifactType), artifacts);
	}

	/**
	 * Gets an artifact type, creating it if it does not already exist. Use this
	 * method to define custom artifact types.
	 *
	 * @param typeName    The type name of the artifact type.
	 * @param displayName The display name of the artifact type.
	 *
	 * @return A type object representing the artifact type.
	 *
	 * @throws BlackboardException If there is a problem getting or adding the
	 *                             artifact type.
	 */
	public synchronized BlackboardArtifact.Type getOrAddArtifactType(String typeName, String displayName) throws BlackboardException {
		if (null == caseDb) {
			throw new BlackboardException("Blackboard has been closed");
		}
		try {
			return caseDb.addBlackboardArtifactType(typeName, displayName);
		} catch (TskDataException typeExistsEx) {
			try {
				return caseDb.getArtifactType(typeName);
			} catch (TskCoreException ex) {
				throw new BlackboardException("Failed to get or add artifact type", ex);
			}
		} catch (TskCoreException ex) {
			throw new BlackboardException("Failed to get or add artifact type", ex);
		}
	}

	/**
	 * Gets an attribute type, creating it if it does not already exist. Use
	 * this method to define custom attribute types.
	 *
	 * @param typeName    The type name of the attribute type.
	 * @param valueType   The value type of the attribute type.
	 * @param displayName The display name of the attribute type.
	 *
	 * @return A type object representing the attribute type.
	 *
	 * @throws BlackboardException If there is a problem getting or adding the
	 *                             attribute type.
	 */
	public synchronized BlackboardAttribute.Type getOrAddAttributeType(String typeName, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType, String displayName) throws BlackboardException {
		if (null == caseDb) {
			throw new BlackboardException("Blackboard has been closed");
		}
		try {
			return caseDb.addArtifactAttributeType(typeName, valueType, displayName);
		} catch (TskDataException typeExistsEx) {
			try {
				return caseDb.getAttributeType(typeName);
			} catch (TskCoreException ex) {
				throw new BlackboardException("Failed to get or add attribute type", ex);
			}
		} catch (TskCoreException ex) {
			throw new BlackboardException("Failed to get or add attribute type", ex);
		}
	}

	/**
	 * Closes the blackboard.
	 *
	 */
	@Override
	public synchronized void close() {
		caseDb = null;
	}

	/**
	 * A Blackboard exception.
	 */
	public static final class BlackboardException extends Exception {

		private static final long serialVersionUID = 1L;

		/**
		 * Constructs a blackboard exception with the specified message.
		 *
		 * @param message The message.
		 */
		BlackboardException(String message) {
			super(message);
		}

		/**
		 * Constructs a blackboard exception with the specified message and
		 * cause.
		 *
		 * @param message The message.
		 * @param cause   The cause.
		 */
		BlackboardException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Event published by SleuthkitCase when one or more artifacts are posted. A
	 * posted artifact is complete (all attributes have been added) and ready
	 * for further processing.
	 */
	final public static class ArtifactsPostedEvent {

		private final ImmutableSet<BlackboardArtifact> artifacts;
		private final String moduleName;
		private final Type artifactType;

		public Type getArtifactType() {
			return artifactType;
		}

		/**
		 * Get the name of the module that posted this artifact.
		 *
		 * @return The name of the module that posted this artifact.
		 */
		public String getModuleName() {
			return moduleName;
		}

		/**
		 * Get the artifacts that were posted.
		 *
		 * @return The artifact.
		 */
		public Collection<BlackboardArtifact> getArtifacts() {
			return artifacts;
		}

		/**
		 * @param moduleName   The name of the module that posted the artifact.
		 * @param artifactType The type of artifacts that were posted. This
		 *                     should match the artifacts, but is not checked.
		 * @param artifacts    The artifacts that were posted.
		 */
		ArtifactsPostedEvent(String moduleName, Type artifactType, Collection<BlackboardArtifact> artifacts) {
			this.moduleName = moduleName;
			this.artifactType = artifactType;
			this.artifacts = ImmutableSet.copyOf(artifacts);
		}

		/**
		 * @param moduleName The name of the module that posted the artifact.
		 * @param artifact   The artifact that was posted.
		 *
		 * @throws org.sleuthkit.datamodel.TskCoreException
		 */
		ArtifactsPostedEvent(String moduleName, BlackboardArtifact artifact) throws TskCoreException {
			this(moduleName, artifact.getArtifactType(), Collections.singleton(artifact));
		}
	}
}
