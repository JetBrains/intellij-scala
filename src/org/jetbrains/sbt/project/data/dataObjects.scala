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
      runtime.ScalaRunTime._equals(this, data)
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
  * @author Pavel Fatin
  */
@SerialVersionUID(1)
case class SbtBuildModuleData(imports: Seq[String], resolvers: Set[SbtResolver]) extends SbtEntityData

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
                          jdk: Option[Sdk],
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
                         jdk: Option[Sdk],
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
  val Key: Key[AndroidFacetData] = datakey(classOf[AndroidFacetData], ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}
