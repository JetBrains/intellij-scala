package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.project.{AbstractExternalEntityData, ModuleData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.bsp.data.BspEntityData._
import org.jetbrains.bsp.{BSP, BspBundle}
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.sbt.project.data.{MyURI, PathData}

import java.nio.file.Path
import java.util
import java.util.Objects
import scala.jdk.CollectionConverters._

abstract class BspEntityData extends AbstractExternalEntityData(BSP.ProjectSystemId) with Product {

  // need to manually specify equals/hashCode here because it is not generated for case classes inheriting from
  // AbstractExternalEntityData
  override def equals(obj: scala.Any): Boolean = obj match {
    case data: BspEntityData =>
      //noinspection CorrespondsUnsorted
      this.canEqual(data) &&
        (this.productIterator sameElements data.productIterator)
    case _ => false
  }

  override def hashCode(): Int = runtime.ScalaRunTime._hashCode(this)
}

object BspEntityData {
  def datakey[T](clazz: Class[T],
                 weight: Int = ProjectKeys.MODULE.getProcessingWeight + 1
                ): Key[T] = new Key(clazz.getName, weight)
}

/**
 * The class is written in a way which allows us to migrate certain fields from one type to another while keeping
 * serialization compatibility. The idea is that the same data is written in two different fields in two different
 * formats, the old format and the new one.
 *
 * After some time, for example, after
 * [[com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage#STORAGE_VERSION]]
 * has been incremented, we can remove the old data fields. We will be guaranteed that a project reload will be forced,
 * which will guarantee that the new field values are populated and serialized.
 *
 * @note The fields must be public, otherwise the Scala 2 compiler mangles the names of the fields, to avoid clashes.
 *       This results in serialization failures.
 *
 * @param vcsRootsCandidates The field must be named exactly [[vcsRootsCandidates]] so the platform External System
 *                           serialization mechanism can deserialize the old project data into it
 * @param _vcsRootsCandidatesPaths The shadow field of [[vcsRootsCandidates]], where we save the same data in a
 *                                 different format, one which we plan to use in the future
 * @param serverDisplayName Corresponds to `InitializeBuildResult.displayName`, examples: `"sbt"`, `"scala-cli"`
 */
// The `java.io.File` fields and the File-based factory/accessor APIs are intentionally retained to preserve
// External System serialization compatibility (see the note above).
//noinspection ApiStatus,SSBasedInspection
class BspProjectData private (
  @Nullable val jdk: SdkReference,
  @NotNull val vcsRootsCandidates: java.util.List[java.io.File],
  @Nullable val _vcsRootsCandidatesPaths: java.util.List[PathData],
  @NotNull val serverDisplayName: String
) extends AbstractExternalEntityData(BSP.ProjectSystemId) with Equals {

  /**
   * Default constructor used by the External System serialization mechanism.
   */
  private def this() = this(
    jdk = null,
    vcsRootsCandidates = java.util.Collections.emptyList(),
    _vcsRootsCandidatesPaths = null,
    serverDisplayName = ""
  )

  //noinspection InstanceOf
  override def canEqual(that: Any): Boolean = that.isInstanceOf[BspProjectData]

  override def equals(obj: Any): Boolean = obj match {
    case that: BspProjectData =>
      //noinspection InstanceOf
      that.canEqual(this) &&
        this.jdkValue == that.jdkValue &&
        this.vcsRootsCandidatesValue == that.vcsRootsCandidatesValue &&
        this. serverDisplayName == that.serverDisplayName
    case _ => false
  }

  override def hashCode(): Int = Objects.hash(jdkValue, vcsRootsCandidatesValue, serverDisplayName)

  private def jdkValue: Option[SdkReference] = Option(jdk)

  private def vcsRootsCandidatesValue: Seq[PathData] = {
    if (_vcsRootsCandidatesPaths == null) {
      if (vcsRootsCandidates != null)
        return vcsRootsCandidates.stream().map[PathData](f => PathData(f.toPath)).toList.asScala.toSeq
      else
        return Seq.empty
    }
    _vcsRootsCandidatesPaths.asScala.toSeq
  }
}

//noinspection SSBasedInspection
object BspProjectData {
  val Key: Key[BspProjectData] = datakey(classOf[BspProjectData], weight = ProjectKeys.PROJECT.getProcessingWeight +  1)

  def apply(sdk: Option[SdkReference], vcsRootsCandidates: Seq[Path], displayName: String): BspProjectData =
    new BspProjectData(
      jdk = sdk.orNull,
      vcsRootsCandidates = vcsRootsCandidates.map(_.toFile).asJava,
      _vcsRootsCandidatesPaths = vcsRootsCandidates.map(PathData.apply).asJava,
      serverDisplayName = displayName
    )

  def unapply(data: BspProjectData): Some[(Option[SdkReference], Seq[PathData], String)] =
    Some((data.jdkValue, data.vcsRootsCandidatesValue, data.serverDisplayName))
}

case class BspTargetCanCompileData @PropertyMapping(Array("compilableTargets"))(
  @NotNull compilableTargets: util.List[String]
) extends BspEntityData

object BspTargetCanCompileData {
  val Key: Key[BspTargetCanCompileData] = datakey(classOf[BspTargetCanCompileData])
}

case class JdkData @PropertyMapping(Array("javaHome", "javaVersion"))(
  @Nullable javaHome: MyURI,
  @Nullable javaVersion: String
) extends BspEntityData

/**
 * @see [[BspProjectData]] for an explanation on the serialization compatibility.
 */
// The `java.io.File` classpath fields and the File-based factory/accessor APIs are intentionally retained to
// preserve External System serialization compatibility (see the @see reference above).
//noinspection SSBasedInspection
class ScalaSdkData private (
  @NotNull val scalaOrganization: String,
  @Nullable val scalaVersion: String,
  @NotNull val scalacClasspath: java.util.List[java.io.File],
  @Nullable val _scalacClasspathPaths: java.util.List[PathData],
  @NotNull val scaladocExtraClasspath: java.util.List[java.io.File],
  @Nullable val _scaladocExtraClasspathPaths: java.util.List[PathData],
  @NotNull val scalacOptions: java.util.List[String]
) extends AbstractExternalEntityData(BSP.ProjectSystemId) with Equals {

  private def this() = this(
    scalaOrganization = "",
    scalaVersion = null,
    scalacClasspath = java.util.Collections.emptyList(),
    _scalacClasspathPaths = null,
    scaladocExtraClasspath = java.util.Collections.emptyList(),
    _scaladocExtraClasspathPaths = null,
    scalacOptions = java.util.Collections.emptyList()
  )

  //noinspection InstanceOf
  override def canEqual(that: Any): Boolean = that.isInstanceOf[ScalaSdkData]

  override def equals(obj: Any): Boolean = obj match {
    case that: ScalaSdkData =>
      that.canEqual(this) &&
        this.scalaOrganization == that.scalaOrganization &&
        this.scalaVersionValue == that.scalaVersionValue &&
        this.scalacClasspathValue == that.scalacClasspathValue &&
        this.scaladocExtraClasspathValue == that.scaladocExtraClasspathValue &&
        this.scalacOptionsValue == that.scalacOptionsValue
    case _ => false
  }

  override def hashCode(): Int =
    Objects.hash(scalaOrganization, scalaVersionValue, scalacClasspathValue, scaladocExtraClasspathValue, scalacOptionsValue)

  private def scalaVersionValue: Option[String] = Option(scalaVersion)

  private def scalacClasspathValue: Seq[PathData] = {
    if (_scalacClasspathPaths == null) {
      return scalacClasspath.stream().map[PathData](f => PathData(f.toPath)).toList.asScala.toSeq
    }
    _scalacClasspathPaths.asScala.toSeq
  }

  private def scaladocExtraClasspathValue: Seq[PathData] = {
    if (_scaladocExtraClasspathPaths == null) {
      return scaladocExtraClasspath.stream().map[PathData](f => PathData(f.toPath)).toList.asScala.toSeq
    }
    _scaladocExtraClasspathPaths.asScala.toSeq
  }

  private def scalacOptionsValue: Seq[String] = scalacOptions.asScala.toSeq
}

//noinspection SSBasedInspection
object ScalaSdkData {
  val Key: Key[ScalaSdkData] = datakey(classOf[ScalaSdkData], weight = ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 10)
  val LibraryName: String = "scala-sdk"

  def apply(
    scalaOrganization: String,
    scalaVersion: String,
    scalacClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    scalacOptions: Seq[String]
  ): ScalaSdkData =
    new ScalaSdkData(
      scalaOrganization = scalaOrganization,
      scalaVersion = scalaVersion,
      scalacClasspath = scalacClasspath.map(_.toFile).asJava,
      _scalacClasspathPaths = scalacClasspath.map(PathData.apply).asJava,
      scaladocExtraClasspath = scaladocExtraClasspath.map(_.toFile).asJava,
      _scaladocExtraClasspathPaths = scaladocExtraClasspath.map(PathData.apply).asJava,
      scalacOptions = scalacOptions.asJava
    )

  def unapply(data: ScalaSdkData): Some[(String, Option[String], Seq[PathData], Seq[PathData], Seq[String])] =
    Some((data.scalaOrganization, data.scalaVersionValue, data.scalacClasspathValue, data.scaladocExtraClasspathValue, data.scalacOptionsValue))
}

case class BspMetadataError(msg: String)

/**
  * Metadata to about bsp targets that have been mapped to IntelliJ modules.
  * @param targetIds target ids mapped to module
  */
case class BspMetadata @PropertyMapping(Array(
  "targetIds",
  "javaHome",
  "javaVersion", "languageLevel"
))(
  @NotNull targetIds: util.List[MyURI],
  @Nullable javaHome: MyURI,
  @Nullable javaVersion: String,
  @Nullable languageLevel: LanguageLevel
)

object BspMetadata {
  val Key: Key[BspMetadata] = datakey(classOf[BspMetadata])
  import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil => ES}

  def get(project: Project, module: Module): Either[BspMetadataError, BspMetadata] = {
    val dataManager = ProjectDataManager.getInstance()

    val moduleId = ES.getExternalProjectId(module)

    def predicate(node: DataNode[ModuleData]) = node.getData.getId == moduleId

    val metadata = for {
      projectInfo <- Option(dataManager.getExternalProjectData(project, BSP.ProjectSystemId, project.getBasePath))
        .toRight(BspMetadataError(BspBundle.message("bsp.metadata.error.project.info", project.getName)))
      projectStructure <- Option(projectInfo.getExternalProjectStructure)
        .toRight(BspMetadataError(BspBundle.message("bsp.metadata.error.project.structure", projectInfo.getExternalProjectPath)))
      moduleDataNode <- Option(ES.findChild(projectStructure, ProjectKeys.MODULE, predicate))
        .toRight(BspMetadataError(BspBundle.message("bsp.metadata.error.data.node", project.getName)))
      metadata <- Option(ES.find(moduleDataNode, BspMetadata.Key))
        .toRight(BspMetadataError(BspBundle.message("bsp.metadata.error.module.metadata", module.getName)))
    } yield {
      metadata.getData
    }
    metadata
  }
}
