package org.jetbrains.sbt.project.data

import java.io.File
import java.net.URI

import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtEntityData._
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.ParsedValue
import org.jetbrains.sbt.resolvers.SbtResolver

abstract class SbtEntityData extends AbstractExternalEntityData(SbtProjectSystem.Id) with Product {

  // need to manually specify equals/hashCode here because it is not generated for case classes inheriting from
  // AbstractExternalEntityData
  override def equals(obj: scala.Any): Boolean = obj match {
    case data: SbtEntityData =>
      //noinspection CorrespondsUnsorted
      this.canEqual(data) &&
        (this.productIterator sameElements data.productIterator)
    case _ => false
  }

  override def hashCode(): Int = runtime.ScalaRunTime._hashCode(this)

}
object SbtEntityData {
  def datakey[T](clazz: Class[T],
                 weight: Int = ProjectKeys.MODULE.getProcessingWeight + 1
                ): Key[T] = new Key(clazz.getName, weight)
}

/**
  * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
  * @param imports implicit sbt file imports.
  * @param resolvers resolvers for this build project
  * @param buildFor id of the project that this module describes the build for
  */
@SerialVersionUID(2)
case class SbtBuildModuleData(imports: Seq[String],
                              resolvers: Set[SbtResolver],
                              buildFor: SbtModuleData) extends SbtEntityData

object SbtBuildModuleData {
  val Key: Key[SbtBuildModuleData] = datakey(classOf[SbtBuildModuleData])
}


/** Data describing a project which is part of an sbt build. */
@SerialVersionUID(1)
case class SbtModuleData(id: String, buildURI: URI) extends SbtEntityData

object SbtModuleData {
  val Key: Key[SbtModuleData] = datakey(classOf[SbtModuleData])
}

@SerialVersionUID(1)
case class SbtProjectData(basePackages: Seq[String],
                          jdk: Option[SdkReference],
                          javacOptions: Seq[String],
                          sbtVersion: String,
                          projectPath: String
                    ) extends SbtEntityData

object SbtProjectData {
  val Key: Key[SbtProjectData] = datakey(classOf[SbtProjectData])
}

sealed trait SbtNamedKey {
  val name: String
}

@SerialVersionUID(1)
case class SbtSettingData(name: String, description: String, rank: Int, value: String)
  extends SbtEntityData with SbtNamedKey
object SbtSettingData {
  val Key: Key[SbtSettingData] = datakey(classOf[SbtSettingData])
}

@SerialVersionUID(1)
case class SbtTaskData(name: String, description: String, rank: Int)
  extends SbtEntityData with SbtNamedKey
object SbtTaskData {
  val Key: Key[SbtTaskData] = datakey(classOf[SbtTaskData])
}

@SerialVersionUID(1)
case class SbtCommandData(name: String, help: Seq[(String,String)])
  extends SbtEntityData with SbtNamedKey
object SbtCommandData {
  val Key: Key[SbtCommandData] = datakey(classOf[SbtCommandData])
}

@SerialVersionUID(1)
case class ModuleExtData(scalaOrganization: String,
                         scalaVersion: Option[Version],
                         scalacClasspath: Seq[File],
                         scalacOptions: Seq[String],
                         jdk: Option[SdkReference],
                         javacOptions: Seq[String]
                   ) extends SbtEntityData

object ModuleExtData {
  val Key: Key[ModuleExtData] = datakey(classOf[ModuleExtData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}


@SerialVersionUID(1)
case class Play2ProjectData(projectKeys: Map[String, Map[String, ParsedValue[_]]]) extends SbtEntityData
object Play2ProjectData {
  val Key: Key[Play2ProjectData] = datakey(classOf[Play2ProjectData], ProjectKeys.PROJECT.getProcessingWeight + 1)
}

@SerialVersionUID(1)
case class AndroidFacetData(version: String, manifest: File, apk: File,
                            res: File, assets: File, gen: File, libs: File,
                            isLibrary: Boolean, proguardConfig: Seq[String]) extends SbtEntityData
object AndroidFacetData {
  // TODO Change to "+ 1" when external system will enable the proper service separation.
  // The external system now invokes data services regardless of system ID.
  // Consequently, com.android.tools.idea.gradle.project.sync.setup.* services in the Android plugin remove _all_ Android facets.
  // As a workaround, we now rely on the additional "weight" to invoke the service after the Android / Gradle's one.
  // We expect the external system to update the architecture so that different services will be properly separated.
  val Key: Key[AndroidFacetData] = datakey(classOf[AndroidFacetData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight +100500)
}
