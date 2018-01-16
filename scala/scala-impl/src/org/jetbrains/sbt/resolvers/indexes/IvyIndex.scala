package org.jetbrains.sbt.resolvers.indexes

import java.io._
import java.util.Properties
import java.util.jar.JarFile

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.{DataExternalizer, EnumeratorStringDescriptor, PersistentHashMap}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt._
import org.jetbrains.sbt.resolvers._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.xml.XML

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
class IvyIndex(val root: String, val name: String, implicit val project: ProjectContext) extends ResolverIndex {
  import ResolverIndex._

  private val indexDir: File = getIndexDirectory(root)
  ensureIndexDir()
  private var artifactToGroupMap = createPersistentMap(indexDir / Paths.ARTIFACT_TO_GROUP_FILE)
  private var groupToArtifactMap = createPersistentMap(indexDir / Paths.GROUP_TO_ARTIFACT_FILE)
  private var groupArtifactToVersionMap = createPersistentMap(indexDir / Paths.GROUP_ARTIFACT_TO_VERSION_FILE)
  private var fqNameToGroupArtifactVersionMap = createPersistentMap(indexDir / Paths.FQ_NAME_TO_GROUP_ARTIFACT_VERSION_FILE)
  private var (_, _, innerTimestamp, currentVersion) = loadProps()

  private def checkStorage(): Unit = {
    if (artifactToGroupMap.isCorrupted ||
        groupToArtifactMap.isCorrupted ||
        groupArtifactToVersionMap.isCorrupted ||
        fqNameToGroupArtifactVersionMap.isCorrupted ||
        currentVersion.toInt < CURRENT_INDEX_VERSION.toInt)
    {
      close()
      deleteIndex()
      artifactToGroupMap = createPersistentMap(indexDir / Paths.ARTIFACT_TO_GROUP_FILE)
      groupToArtifactMap = createPersistentMap(indexDir / Paths.GROUP_TO_ARTIFACT_FILE)
      groupArtifactToVersionMap = createPersistentMap(indexDir / Paths.GROUP_ARTIFACT_TO_VERSION_FILE)
      fqNameToGroupArtifactVersionMap = createPersistentMap(indexDir / Paths.FQ_NAME_TO_GROUP_ARTIFACT_VERSION_FILE)
      val (_, _, a, b) = loadProps()
      innerTimestamp = a
      currentVersion = b
      SbtIndexesManager.getInstance(project).get.doUpdateResolverIndexWithProgress("Local Ivy cache", this)
    }
  }

  private def withStorageCheck[T](f: => Set[T]): Set[T] = {
    try     { f }
    catch   { case _: Exception => Set.empty }
    finally { checkStorage() }
  }

  override def searchGroup(artifactId: String): Set[String] = {
    withStorageCheck {
      if (artifactId.isEmpty)
        Option(groupToArtifactMap.getAllKeysWithExistingMapping)
          .map {_.asScala.toSet}
          .getOrElse(Set.empty)
      else
        Option(artifactToGroupMap.get(artifactId)).getOrElse(Set.empty)
    }
  }

  override def searchArtifact(groupId: String): Set[String] = {
    withStorageCheck {
      if (groupId.isEmpty)
        Option(artifactToGroupMap.getAllKeysWithExistingMapping)
        .map {_.asScala.toSet}
        .getOrElse (Set.empty)
      else
        Option(groupToArtifactMap.get(groupId)).getOrElse(Set.empty)
    }
  }

  override def searchVersion(groupId: String, artifactId: String): Set[String] = {
    withStorageCheck {
      Option(groupArtifactToVersionMap.get(SbtResolverUtils.joinGroupArtifact(groupId, artifactId))).getOrElse(Set.empty)
    }
  }

  override def searchArtifactInfo(fqName: String)(implicit project: ProjectContext): Set[ArtifactInfo] = {
    withStorageCheck {
      Option(fqNameToGroupArtifactVersionMap.get(fqName)).getOrElse(Set.empty).map(s => {
        val info: Array[String] = s.split(":")
        ArtifactInfo(info(0), info(1), info(2))
      })
    }
  }


  override def doUpdate(progressIndicator: Option[ProgressIndicator] = None): Unit = {

    if (ApplicationManager.getApplication.isUnitTestMode && sys.props.get(FORCE_UPDATE_KEY).isEmpty)
      return

    checkStorage()
    val agMap  = mutable.HashMap.empty[String, mutable.Set[String]]
    val gaMap  = mutable.HashMap.empty[String, mutable.Set[String]]
    val gavMap = mutable.HashMap.empty[String, mutable.Set[String]]
    val fqNameGavMap = mutable.HashMap.empty[String, mutable.Set[String]]

    def processArtifact(artifact: ArtifactInfo) {
      progressIndicator foreach { _.checkCanceled() }
      agMap.getOrElseUpdate(artifact.artifactId, mutable.Set.empty) += artifact.groupId
      gaMap.getOrElseUpdate(artifact.groupId, mutable.Set.empty) += artifact.artifactId
      gavMap.getOrElseUpdate(SbtResolverUtils.joinGroupArtifact(artifact), mutable.Set.empty) += artifact.version
    }

    def processFqNames(fqNameArtifacts: (String, mutable.Set[ArtifactInfo])): Unit = {
      val fqName = fqNameArtifacts._1
      val artifacts = fqNameArtifacts._2.map(a => a.groupId + ":" + a.artifactId + ":" + a.version)
      fqNameGavMap.getOrElseUpdate(fqName, mutable.Set.empty) ++= artifacts
    }

    val ivyCacheEnumerator = new SbtIvyCacheEnumerator(new File(root), progressIndicator)
    ivyCacheEnumerator.artifacts.foreach(processArtifact)
    ivyCacheEnumerator.fqNameToArtifacts.foreach(processFqNames)

    progressIndicator foreach { _.checkCanceled() }
    progressIndicator foreach { _.setText2(SbtBundle("sbt.resolverIndexer.progress.saving")) }

    def mergeIntoMap(map: PersistentHashMap[String, Set[String]])(element: (String, mutable.Set[String])): Unit= {
      val existingValue = Option(map.get(element._1)).getOrElse(Set.empty)
      map.put(element._1, existingValue ++ element._2)
    }

    agMap  foreach { mergeIntoMap(artifactToGroupMap) }
    gaMap  foreach { mergeIntoMap(groupToArtifactMap) }
    gavMap foreach { mergeIntoMap(groupArtifactToVersionMap) }
    fqNameGavMap foreach { mergeIntoMap(fqNameToGroupArtifactVersionMap) }

    innerTimestamp = System.currentTimeMillis()
    store()
  }

  override def getUpdateTimeStamp: Long = innerTimestamp

  private def deleteIndex(): Unit = SbtIndexesManager.cleanUpCorruptedIndex(indexDir)

  private def store(): Unit = {
    ensureIndexDir()
    storeProps()
    artifactToGroupMap.force()
    groupToArtifactMap.force()
    groupArtifactToVersionMap.force()
    fqNameToGroupArtifactVersionMap.force()
  }

  override def close(): Unit = {
    store()
    artifactToGroupMap.close()
    groupToArtifactMap.close()
    groupArtifactToVersionMap.close()
    fqNameToGroupArtifactVersionMap.close()
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

  private def storeProps(): Unit = {
    ensureIndexDir()

    val props = new Properties()
    props.setProperty(Keys.VERSION, CURRENT_INDEX_VERSION)
    props.setProperty(Keys.ROOT, root)
    props.setProperty(Keys.UPDATE_TIMESTAMP, innerTimestamp.toString)
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

  private[indexes] class SetDescriptor extends DataExternalizer[Set[String]] {
    def save(s: DataOutput, set: Set[String]) {
      s.writeLong(set.size)
      set foreach s.writeUTF
    }

    def read(s: DataInput): Set[String] = {
      val count = s.readLong

      1L.to(count).map(_ => s.readUTF()).toSet
    }
  }

  private[indexes] class SbtIvyCacheEnumerator(val cacheDir: File, progressIndicator: Option[ProgressIndicator]) {

    val fqNameToArtifacts: mutable.Map[String, mutable.Set[ArtifactInfo]] = mutable.Map.empty

    private val ivyFileFilter = new FileFilter {
      override def accept(file: File): Boolean = file.name.endsWith(".xml")
    }

    def artifacts: Stream[ArtifactInfo] = listArtifacts(cacheDir)

    private def fqNamesFromJarFile(file: File): Stream[String] = {
      progressIndicator.foreach(_.setText2(file.getAbsolutePath))

      val jarFile = new JarFile(file)

      val classExt = ".class"

      val entries = jarFile.entries().asScala
        .filter(e => (e.getName.endsWith(classExt) && !e.getName.contains("$")) ||
          e.getName.endsWith("/") || e.getName.endsWith("\\"))

      entries
        .map(e => e.getName)
        .map(name => name.replaceAll("/", "."))
        .map(name => if (name.endsWith(classExt)) name.substring(0, name.length - classExt.length) else name)
        .toStream
    }

    private def listFqNames(dir: File, artifacts: Stream[ArtifactInfo]): mutable.Map[ArtifactInfo, Set[String]] = {
      val artifactToFqNames = mutable.HashMap.empty[ArtifactInfo, Set[String]]

      var jarsDir = new File(dir, "jars")
      if (!jarsDir.exists) {
        jarsDir = new File(dir, "bundles")
        if (!jarsDir.exists())
          return artifactToFqNames
      }

      artifacts.foreach((t: ArtifactInfo) => {
        val fname = t.artifactId + "-" + t.version + ".jar"
        val jarFile = new File(jarsDir, fname)
        if (jarFile.exists) {
          artifactToFqNames.put(t, fqNamesFromJarFile(jarFile).toSet)
        }
      })

      artifactToFqNames
    }

    private def listArtifacts(dir: File): Stream[ArtifactInfo] = {
      if (!dir.isDirectory)
        throw InvalidRepository(dir.getAbsolutePath)

      val artifactsHere = dir.listFiles(ivyFileFilter)
          .flatMap(extractArtifact)
          .filterNot(artifact => searchVersion(artifact.groupId, artifact.artifactId).contains(artifact.version))
          .toStream

      if (artifactsHere.nonEmpty) {
        val artifactToFqNames = listFqNames(dir, artifactsHere)
        for {
          (artifact, fqNames) <- artifactToFqNames
          fqName <- fqNames
        } {
          fqNameToArtifacts.getOrElseUpdate(fqName, mutable.Set.empty) += artifact
        }
      }
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