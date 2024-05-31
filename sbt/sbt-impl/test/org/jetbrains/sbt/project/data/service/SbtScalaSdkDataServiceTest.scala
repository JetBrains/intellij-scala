package org.jetbrains.sbt.project.data.service

import org.junit.Assert.assertTrue
import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ProjectExt}
import org.jetbrains.sbt.project.data._
import org.junit.Assert._

import java.io.File
import java.net.URI

class SbtScalaSdkDataServiceTest extends SbtModuleDataServiceTestCase {

  import ExternalSystemDataDsl._

  def testWithoutScalaLibrary(): Unit =
    importProjectData(generateScalaProject("2.11.5", None, Seq.empty))

  def testWithIncompatibleScalaLibrary(): Unit = {
    importProjectData(generateScalaProject("2.11.5", Some("2.10.4"), Seq.empty))
    //assertScalaLibraryWarningNotificationShown(getProject, SbtProjectSystem.Id)
    assertNoNotificationShown(getProject)
  }

  def testWithCompatibleScalaLibrary(): Unit = {
    doTestAndCheckScalaSdk("2.11.1", "2.11.5")
    doTestAndCheckScalaSdk("2.10.4", "2.10.3")
  }

  def testWithTheSameVersionOfScalaLibrary(): Unit = {
    doTestAndCheckScalaSdk("2.11.6", "2.11.6")
    doTestAndCheckScalaSdk("2.10.4", "2.10.4")
    doTestAndCheckScalaSdk("2.9.2", "2.9.2")
  }

  def testScalaSdkForEvictedVersion(): Unit = {
    val evictedVersion = "2.11.2"
    val newVersion = "2.11.6"

    val projectData = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new SbtProjectNode(SbtProjectData(None, "", getProject.getBasePath, projectTransitiveDependenciesUsed = false, prodTestSourcesSeparated = false))

      val evictedScalaLibrary: library = new library {
        name := s"org.scala-lang:scala-library:$evictedVersion"
      }
      val newScalaLibrary: library = new library {
        name := s"org.scala-lang:scala-library:$newVersion"
      }
      libraries ++= Seq(evictedScalaLibrary, newScalaLibrary)

      modules += new javaModule {
        val uri: URI = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        libraryDependencies += newScalaLibrary
        arbitraryNodes ++= Seq(
          new ModuleExtNode(SbtModuleExtData(Some(evictedVersion))),
          new ScalaSdkNode(SbtScalaSdkData(Some(evictedVersion)))
        )
      }
    }.build.toDataNode

    importProjectData(projectData)

    val scalaSdksCount = scalaSdks(evictedVersion).size
    assertTrue(s"More or less than one ScalaSdk for $evictedVersion scala version is set up in the project libraries", scalaSdksCount == 1)
  }

  private def doTestAndCheckScalaSdk(scalaVersion: String, scalaLibraryVersion: String): Unit = {
    importProjectData(generateScalaProject(scalaVersion, Some(scalaLibraryVersion), Seq.empty))

    checkScalaSdksInModules()
    val isScalaSdkSetUp = scalaSdks(scalaVersion).nonEmpty
    assertTrue(s"ScalaSdk for $scalaVersion scala version is not set up in the project libraries", isScalaSdkSetUp)
  }

  private def checkScalaSdksInModules(): Unit = {
    getProject.modules.filter(ModuleType.get(_).getName == JavaModuleType.getModuleName).foreach { module =>
      val scalaSdkLibraries = module.libraries.filter(_.isScalaSdk)
      scalaSdkLibraries.size match {
        case 0 => fail(s"ScalaSdk is not set up in the module ${module.getName}")
        case 1 => //ok
        case _ => fail(s"ScalaSdk is set more than once in the module ${module.getName}")
      }
    }
  }
}
