package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{LibraryExt, ProjectExt}
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.sbt.project.data._

import java.io.File
import java.net.URI

trait SbtModuleDataServiceTestCase extends ProjectDataServiceTestCase {

  import ExternalSystemDataDsl._

  override def getModule: Module =
    ModuleManager.getInstance(getProject).findModuleByName("Module 1")

  override def setUp(): Unit = {
    super.setUp()
    setUpJdks()
  }

  override def tearDown(): Unit = {
    tearDownJdks()
    super.tearDown()
  }

  protected def generateScalaProject(
    scalaVersion: String,
    scalaLibraryVersion: Option[String],
    scalacOptions: Seq[String]
  ): DataNode[ProjectData] =
    generateProject(Some(scalaVersion), scalaLibraryVersion, scalacOptions, None, Seq.empty)

  protected def generateProject(
    scalaVersion: Option[String],
    scalaLibraryVersion: Option[String],
    scalacOptions: Seq[String],
    sdk: Option[SdkReference],
    javacOptions: Seq[String]
  ): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new SbtProjectNode(SbtProjectData(None, "", getProject.getBasePath, projectTransitiveDependenciesUsed = false, prodTestSourcesSeparated = false))

      val scalaLibrary: Option[library] = scalaLibraryVersion.map { version =>
        new library {
          name := "org.scala-lang:scala-library:" + version
        }
      }
      scalaLibrary.foreach(libraries += _)

      modules += new javaModule {
        val uri: URI = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        scalaLibrary.foreach(libraryDependencies += _)
        arbitraryNodes ++= Seq(
          new ModuleExtNode(SbtModuleExtData(
            scalaVersion = scalaVersion,
            scalacOptions = scalacOptions,
            sdk = sdk,
            javacOptions = javacOptions)),
          new ScalaSdkNode(SbtScalaSdkData(scalaVersion))
        )
      }
    }.build.toDataNode

  protected def scalaSdks(scalaVersion: String): Seq[Library] = {
    getProject.libraries
      .filter(_.getName.contains(s"sbt: scala-sdk-$scalaVersion"))
      .filter(_.isScalaSdk)
  }

  private def setUpJdks(): Unit = inWriteAction {
    val projectJdkTable = ProjectJdkTable.getInstance()
    projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    projectJdkTable.addJdk(IdeaTestUtil.getMockJdk17)
    projectJdkTable.addJdk(IdeaTestUtil.getMockJdk18)
    // TODO: find a way to create mock Android SDK
  }

  private def tearDownJdks(): Unit = inWriteAction {
    val projectJdkTable = ProjectJdkTable.getInstance()
    projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
  }
}
