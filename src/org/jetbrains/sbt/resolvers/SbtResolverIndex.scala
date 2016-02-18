package org.jetbrains.sbt
package resolvers

import java.io._
import java.util.Properties

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.{PersistentEnumeratorBase, DataExternalizer, EnumeratorStringDescriptor, PersistentHashMap}
import org.apache.maven.index.ArtifactInfo

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.language.reflectiveCalls

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */
class SbtResolverIndex private (val kind: SbtResolver.Kind.Value, val root: String, var timestamp: Long, val indexDir: File) {
  import org.jetbrains.sbt.resolvers.SbtResolverIndex._

  ensureIndexDir()
  private val artifactToGroupMap = createPersistentMap(indexDir / Paths.ARTIFACT_TO_GROUP_FILE)
  private val groupToArtifactMap = createPersistentMap(indexDir / Paths.GROUP_TO_ARTIFACT_FILE)
  private val groupArtifactToVersionMap = createPersistentMap(indexDir / Paths.GROUP_ARTIFACT_TO_VERSION_FILE)


  def update(progressIndicator: Option[ProgressIndicator] = None) {
    val agMap  = mutable.HashMap.empty[String, mutable.Set[String]]
    val gaMap  = mutable.HashMap.empty[String, mutable.Set[String]]
    val gavMap = mutable.HashMap.empty[String, mutable.Set[String]]
    def processArtifact(artifact: ArtifactInfo) {
      agMap.getOrElseUpdate(artifact.getArtifactId, mutable.Set.empty) += artifact.getGroupId
      gaMap.getOrElseUpdate(artifact.getGroupId, mutable.Set.empty) += artifact.getArtifactId
      gavMap.getOrElseUpdate(SbtResolverUtils.joinGroupArtifact(artifact), mutable.Set.empty) += artifact.getVersion
    }

    if (kind == SbtResolver.Kind.Maven)
      using(SbtMavenRepoIndexer(root, indexDir)) { indexer =>
        indexer.update(progressIndicator)
        indexer.foreach(processArtifact, progressIndicator)
      }
    else
      new SbtIvyCacheIndexer(new File(root)).artifacts.foreach(processArtifact)

    progressIndicator foreach { _.checkCanceled() }
    progressIndicator foreach { _.setText2(SbtBundle("sbt.resolverIndexer.progress.saving")) }

    agMap  foreach { element => artifactToGroupMap.put(element._1, element._2.toSet) }
    gaMap  foreach { element => groupToArtifactMap.put(element._1, element._2.toSet) }
    gavMap foreach { element => groupArtifactToVersionMap.put(element._1, element._2.toSet) }
    timestamp = System.currentTimeMillis()
    store()
  }

  def store() {
    ensureIndexDir()

    val props = new Properties()
    props.setProperty(Keys.VERSION, CURRENT_INDEX_VERSION)
    props.setProperty(Keys.ROOT, root)
    props.setProperty(Keys.UPDATE_TIMESTAMP, timestamp.toString)
    props.setProperty(Keys.KIND, kind.toString)

    val propFile = indexDir / Paths.PROPERTIES_FILE
    using(new FileOutputStream(propFile)) { outputStream =>
      props.store(outputStream, null)
    }

    artifactToGroupMap.force()
    groupToArtifactMap.force()
    groupArtifactToVersionMap.force()
  }

  def close() {
    artifactToGroupMap.close()
    groupToArtifactMap.close()
    groupArtifactToVersionMap.close()
  }

  def groups() = Option(groupToArtifactMap.getAllKeysWithExistingMapping) map { _.toSet } getOrElse Set.empty
  def groups(artifact: String) = artifactToGroupMap.getOrEmpty(artifact)

  def artifacts() = Option(artifactToGroupMap.getAllKeysWithExistingMapping) map { _.toSet } getOrElse Set.empty
  def artifacts(group: String) = groupToArtifactMap.getOrEmpty(group)

  def versions(group: String, artifact: String) =
    groupArtifactToVersionMap.getOrEmpty(SbtResolverUtils.joinGroupArtifact(group, artifact))

  def isLocal: Boolean = kind == SbtResolver.Kind.Ivy || root.startsWith("file:")

  private def ensureIndexDir() {
    indexDir.mkdirs()
    if (!indexDir.exists || !indexDir.isDirectory)
      throw new CantCreateIndexDirectory(indexDir)
  }

  private def createPersistentMap(file: File) =
    new PersistentHashMap[String, Set[String]](file, new EnumeratorStringDescriptor, new SetDescriptor) {
      def getOrEmpty(key: String): Set[String] =
        try {
          Option(get(key)).getOrElse(Set.empty)
        } catch {
          case _: PersistentEnumeratorBase.CorruptedException | _: EOFException =>
            throw new CorruptedIndexException(file)
        }
    }
}

object SbtResolverIndex {
  val NO_TIMESTAMP = -1

  val CURRENT_INDEX_VERSION = "2"

  object Paths {
    val PROPERTIES_FILE = "index.properties"
    val ARTIFACT_TO_GROUP_FILE = "artifact-to-group.map"
    val GROUP_TO_ARTIFACT_FILE = "group-to-artifact.map"
    val GROUP_ARTIFACT_TO_VERSION_FILE = "group-artifact-to-version.map"
  }

  object Keys {
    val VERSION = "version"
    val ROOT = "root"
    val UPDATE_TIMESTAMP = "update-timestamp"
    val KIND = "kind"
  }

  def create(kind: SbtResolver.Kind.Value, root: String, indexDir: File) = {
    val index = new SbtResolverIndex(kind, root, NO_TIMESTAMP, indexDir)
    index.store()
    index
  }

  def load(indexDir: File) = {
    val propFile = indexDir / Paths.PROPERTIES_FILE
    val props = new Properties()

    using(new FileInputStream(propFile)) { inputStream =>
      props.load(inputStream)
    }

    val indexVersion = props.getProperty(Keys.VERSION)
    if (indexVersion != CURRENT_INDEX_VERSION)
      throw new IndexVersionMismatch(propFile)

    val root = props.getProperty(Keys.ROOT)
    val timestamp = props.getProperty(Keys.UPDATE_TIMESTAMP).toLong
    val kind = {
      if (props.getProperty(Keys.KIND) == SbtResolver.Kind.Maven.toString)
        SbtResolver.Kind.Maven
      else
        SbtResolver.Kind.Ivy
    }

    new SbtResolverIndex(kind, root, timestamp, indexDir)
  }
}

private class SetDescriptor extends DataExternalizer[Set[String]] {
  def save(s: DataOutput, set: Set[String]) {
    s.writeInt(set.size)
    set foreach s.writeUTF
  }

  def read(s: DataInput): Set[String] = {
    val count = s.readInt
    1.to(count).map(_ => s.readUTF()).toSet
  }
}
