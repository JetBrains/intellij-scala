package org.jetbrains.sbt.resolvers.indexes

import java.io._
import java.util.Properties

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.{DataExternalizer, EnumeratorStringDescriptor, PersistentHashMap}
import org.jetbrains.sbt._
import org.jetbrains.sbt.resolvers._

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.xml.XML

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
class IvyIndex(val root: String, val name: String) extends ResolverIndex {
  import ResolverIndex._

  private val indexDir: File = getIndexDirectory(root)
  ensureIndexDir()
  private val artifactToGroupMap = createPersistentMap(indexDir / Paths.ARTIFACT_TO_GROUP_FILE)
  private val groupToArtifactMap = createPersistentMap(indexDir / Paths.GROUP_TO_ARTIFACT_FILE)
  private val groupArtifactToVersionMap = createPersistentMap(indexDir / Paths.GROUP_ARTIFACT_TO_VERSION_FILE)
  private var (_, _, innerTimestamp, _) = loadProps()

  private def checkStorage(): Unit = {
    if (artifactToGroupMap.isCorrupted ||
        groupToArtifactMap.isCorrupted ||
        groupArtifactToVersionMap.isCorrupted)
      deleteIndex()
  }

  private def withStorageCheck[T](f: => Set[T]): Set[T] = {
    try     { f }
    catch   { case _: Exception => Set.empty }
    finally { checkStorage() }
  }

  override def searchGroup(artifactId: String)(implicit project: Project): Set[String] = {
    withStorageCheck {
      if (artifactId.isEmpty)
        Option(groupToArtifactMap.getAllKeysWithExistingMapping) map {
          _.toSet
        } getOrElse Set.empty
      else
        Option(artifactToGroupMap.get(artifactId)).getOrElse(Set.empty)
    }
  }

  override def searchArtifact(groupId: String)(implicit project: Project): Set[String] = {
    withStorageCheck {
      if (groupId.isEmpty)
        Option(artifactToGroupMap.getAllKeysWithExistingMapping) map {
          _.toSet
        } getOrElse Set.empty
      else
        Option(groupToArtifactMap.get(groupId)).getOrElse(Set.empty)
    }
  }

  override def searchVersion(groupId: String, artifactId: String)(implicit project: Project): Set[String] = {
    withStorageCheck {
      Option(groupArtifactToVersionMap.get(SbtResolverUtils.joinGroupArtifact(groupId, artifactId))).getOrElse(Set.empty)
    }
  }
  override def doUpdate(progressIndicator: Option[ProgressIndicator] = None)(implicit project: Project): Unit = {
    val agMap  = mutable.HashMap.empty[String, mutable.Set[String]]
    val gaMap  = mutable.HashMap.empty[String, mutable.Set[String]]
    val gavMap = mutable.HashMap.empty[String, mutable.Set[String]]
    def processArtifact(artifact: ArtifactInfo) {
      progressIndicator foreach { _.checkCanceled() }
      agMap.getOrElseUpdate(artifact.artifactId, mutable.Set.empty) += artifact.groupId
      gaMap.getOrElseUpdate(artifact.groupId, mutable.Set.empty) += artifact.artifactId
      gavMap.getOrElseUpdate(SbtResolverUtils.joinGroupArtifact(artifact), mutable.Set.empty) += artifact.version
    }
    new SbtIvyCacheEnumerator(new File(root)).artifacts.foreach(processArtifact)

    progressIndicator foreach { _.checkCanceled() }
    progressIndicator foreach { _.setText2(SbtBundle("sbt.resolverIndexer.progress.saving")) }

    agMap  foreach { element => artifactToGroupMap.put(element._1, element._2.toSet) }
    gaMap  foreach { element => groupToArtifactMap.put(element._1, element._2.toSet) }
    gavMap foreach { element => groupArtifactToVersionMap.put(element._1, element._2.toSet) }

    innerTimestamp = System.currentTimeMillis()
    store()
  }

  override def getUpdateTimeStamp(implicit project: Project): Long = innerTimestamp

  private def deleteIndex() = SbtIndexesManager.cleanUpCorruptedIndex(indexDir)

  private def store(): Unit = {
    ensureIndexDir()
    storeProps()
    artifactToGroupMap.force()
    groupToArtifactMap.force()
    groupArtifactToVersionMap.force()
  }

  override def close(): Unit = {
    store()
    artifactToGroupMap.close()
    groupToArtifactMap.close()
    groupArtifactToVersionMap.close()
  }

  private def loadProps() = {
    val propFile = indexDir / Paths.PROPERTIES_FILE
    val props = new Properties()

    try {
      using(new FileInputStream(propFile)) { inputStream =>
        props.load(inputStream)
      }
    } catch {
      case _: IOException => // ignore, props will be recreated
    }

    (
      props.getProperty(Keys.KIND, "ivy"),
      props.getProperty(Keys.ROOT, root),
      props.getProperty(Keys.UPDATE_TIMESTAMP, "-1").toLong,
      props.getProperty(Keys.VERSION, CURRENT_INDEX_VERSION)
    )
  }

  private def storeProps() = {
    ensureIndexDir()

    val props = new Properties()
    props.setProperty(Keys.VERSION, CURRENT_INDEX_VERSION)
    props.setProperty(Keys.ROOT, root)
    props.setProperty(Keys.UPDATE_TIMESTAMP, getUpdateTimeStamp(null).toString)
    props.setProperty(Keys.KIND, "ivy")

    val propFile = indexDir / Paths.PROPERTIES_FILE
    using(new FileOutputStream(propFile)) { outputStream =>
      props.store(outputStream, null)
    }
  }

  private def ensureIndexDir() {
    indexDir.mkdirs()
    if (!indexDir.exists || !indexDir.isDirectory)
      throw CantCreateIndexDirectory(indexDir)
  }

  private def createPersistentMap(file: File): PersistentHashMap[String, Set[String]] =
    new PersistentHashMap[String, Set[String]](file, new EnumeratorStringDescriptor, new SetDescriptor)

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

  class SbtIvyCacheEnumerator(val cacheDir: File) {

    def artifacts: Stream[ArtifactInfo] = listArtifacts(cacheDir)

    private val ivyFileFilter = new FileFilter {
      override def accept(file: File): Boolean = file.name.endsWith(".xml") && file.lastModified() > innerTimestamp
    }

    private def listArtifacts(dir: File): Stream[ArtifactInfo] = {
      if (!dir.isDirectory)
        throw InvalidRepository(dir.getAbsolutePath)
      val artifactsHere = dir.listFiles(ivyFileFilter).flatMap(extractArtifact).toStream
      artifactsHere ++ dir.listFiles.toStream.filter(_.isDirectory).flatMap(listArtifacts)
    }

    private def extractArtifact(ivyFile: File): Option[ArtifactInfo] = {
      try {
        val xml = XML.loadFile(ivyFile)
        val group    = (xml \\ "ivy-module" \\ "info" \\ "@organisation").text
        val artifact = (xml \\ "ivy-module" \\ "info" \\ "@module").text
        val version  = (xml \\ "ivy-module" \\ "info" \\ "@revision").text
        Some(ArtifactInfo(group, artifact, version))
      } catch {
        case e : Throwable => None
      }
    }
  }

}