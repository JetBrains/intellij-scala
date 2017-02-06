package org.jetbrains.sbt.project.data

import java.io.File
import java.net.URI

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.ParsedValue
import org.jetbrains.sbt.resolvers.SbtResolver



/**
  * Data describing a "build" module: The IDEA-side representation of the sbt meta-project
  * @author Pavel Fatin
  */
class SbtBuildModuleData(val owner: ProjectSystemId, val imports: Seq[String], val resolvers: Set[SbtResolver])
  extends AbstractExternalEntityData(owner)

object SbtBuildModuleData {
  val Key: Key[SbtBuildModuleData] = new Key(classOf[SbtBuildModuleData].getName,
    ProjectKeys.MODULE.getProcessingWeight + 1)
}


/**
  * Data describing a project which is part of an sbt build.
  * Created by jast on 2016-12-12.
  */
case class SbtModuleData(owner: ProjectSystemId, id: String, buildURI: URI)
  extends AbstractExternalEntityData(owner)

object SbtModuleData {
  val Key: Key[SbtModuleData] =
    new Key(classOf[SbtModuleData].getName,
      ProjectKeys.MODULE.getProcessingWeight + 1)
}


class SbtProjectData(val owner: ProjectSystemId,
                     val basePackages: Seq[String],
                     val jdk: Option[Sdk],
                     val javacOptions: Seq[String],
                     val sbtVersion: String,
                     val projectPath: String
                    ) extends AbstractExternalEntityData(owner)

object SbtProjectData {
  val Key: Key[SbtProjectData] = new Key(classOf[SbtProjectData].getName,
    ProjectKeys.MODULE.getProcessingWeight + 1)
}

class SbtSettingData(val owner: ProjectSystemId, val label: String, val description: String, val rank: Int) extends AbstractExternalEntityData(owner)
object SbtSettingData {
  val Key: Key[SbtSettingData] = new Key(classOf[SbtSettingData].getName, SbtProjectData.Key.getProcessingWeight + 1)
}

class SbtTaskData(val owner: ProjectSystemId, val label: String, val description: String, val rank: Int) extends AbstractExternalEntityData(owner)
object SbtTaskData {
  val Key: Key[SbtTaskData] = new Key(classOf[SbtTaskData].getName, SbtProjectData.Key.getProcessingWeight + 1)
}


class ModuleExtData(val owner: ProjectSystemId,
                    val scalaVersion: Option[Version],
                    val scalacClasspath: Seq[File],
                    val scalacOptions: Seq[String],
                    val jdk: Option[Sdk],
                    val javacOptions: Seq[String]
                   ) extends AbstractExternalEntityData(owner)

object ModuleExtData {
  val Key: Key[ModuleExtData] = new Key(classOf[ModuleExtData].getName,
    ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}



class Play2ProjectData(val owner: ProjectSystemId, val projectKeys: Map[String, Map[String, ParsedValue[_]]])
  extends AbstractExternalEntityData(owner)

object Play2ProjectData {
  val Key: Key[Play2ProjectData] = new Key(classOf[Play2ProjectData].getName,
    ProjectKeys.PROJECT.getProcessingWeight + 1)
}

class AndroidFacetData(val owner: ProjectSystemId,
                       val version: String, val manifest: File, val apk: File,
                       val res: File, val assets: File, val gen: File, val libs: File,
                       val isLibrary: Boolean, val proguardConfig: Seq[String])
  extends AbstractExternalEntityData(owner)

object AndroidFacetData {
  val Key: Key[AndroidFacetData] = new Key(classOf[AndroidFacetData].getName,
    ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
}
