package org.jetbrains.bsp.data

import java.io.File
import java.net.URI
import java.util
import com.intellij.openapi.externalSystem.model.project.{AbstractExternalEntityData, ModuleData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.bsp.{BSP, BspBundle}
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.bsp.data.BspEntityData._

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

@SerialVersionUID(1)
case class BspProjectData @PropertyMapping(Array("jdk", "vcsRootsCandidates")) private (
  @Nullable jdk: SdkReference,
  @NotNull vcsRootsCandidates: util.List[File]
) extends BspEntityData

object BspProjectData {
  val Key: Key[BspProjectData] = datakey(classOf[BspProjectData], weight = ProjectKeys.PROJECT.getProcessingWeight +  1)
  def apply(sdk: Option[SdkReference], vcsRootsCandidates: util.List[File]): BspProjectData =
    BspProjectData(sdk.orNull, vcsRootsCandidates)
}


case class JdkData @PropertyMapping(Array("javaHome", "javaVersion"))(
  @Nullable javaHome: URI,
  @Nullable javaVersion: String
) extends BspEntityData

@SerialVersionUID(3)
case class ScalaSdkData @PropertyMapping(Array("scalaOrganization", "scalaVersion", "scalacClasspath", "scalacOptions"))(
  @NotNull scalaOrganization: String,
  @Nullable scalaVersion: String,
  @NotNull scalacClasspath: util.List[File],
  @NotNull scalacOptions: util.List[String]
) extends BspEntityData

object ScalaSdkData {
  val Key: Key[ScalaSdkData] = datakey(classOf[ScalaSdkData], weight = ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 10)
  val LibraryName: String = "scala-sdk"
}

case class BspMetadataError(msg: String)

/**
  * Metadata to about bsp targets that have been mapped to IntelliJ modules.
  * @param targetIds target ids mapped to module
  */
@SerialVersionUID(4)
case class BspMetadata @PropertyMapping(Array("targetIds", "javaHome", "javaVersion", "languageLevel"))
(@NotNull targetIds: util.List[URI], @Nullable javaHome: URI, @Nullable javaVersion: String, @Nullable languageLevel: LanguageLevel)

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
      moduleDataNode <- Option(ES.find(projectStructure, ProjectKeys.MODULE, predicate))
        .toRight(BspMetadataError(BspBundle.message("bsp.metadata.error.data.node", project.getName)))
      metadata <- Option(ES.find(moduleDataNode, BspMetadata.Key))
        .toRight(BspMetadataError(BspBundle.message("bsp.metadata.error.module.metadata", module.getName)))
    } yield {
      metadata.getData
    }
    metadata
  }
}
