package org.jetbrains.sbt.project

import java.io.File

import com.intellij.openapi.externalSystem.model.{ExternalSystemException, DataNode}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.UsefulTestCaseHelper
import org.jetbrains.sbt.project.data.ScalaSdkNode

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
class ScalaSdkDataServiceTest extends ProjectDataServiceTestCase with UsefulTestCaseHelper {

  import ExternalSystemDsl._

  def generateProject(scalaVersion: String, scalaLibraryVersion: Option[String]): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val scalaLibrary = scalaLibraryVersion.map { version =>
        new library { name := "org.scala-lang:scala-library:" + version }
      }
      scalaLibrary.foreach(libraries += _)

      modules += new javaModule {
        name := "Module 1"
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        scalaLibrary.foreach(libraryDependencies += _)
        arbitraryNodes += new ScalaSdkNode(Version(scalaVersion), "", Seq.empty, Seq.empty)
      }
    }.build.toDataNode

  def testWithoutScalaLibrary(): Unit =
    importProjectData(generateProject("2.11.5", None))

  def testWithIncompatibleScalaLibrary(): Unit =
    assertException[ExternalSystemException](Some("Cannot find project Scala library 2.11.5 for module Module 1")) {
      importProjectData(generateProject("2.11.5", Some("2.10.4")))
    }

  def doTestAndCheckScalaSdk(scalaVersion: String, scalaLibraryVersion: String): Unit = {
    import org.jetbrains.plugins.scala.project._
    importProjectData(generateProject(scalaVersion, Some(scalaLibraryVersion)))
    val isLibrarySetUp = ProjectLibraryTable.getInstance(getProject).getLibraries.filter(_.getName.contains("scala-library")).exists(_.isScalaSdk)
    assert(isLibrarySetUp, "Scala library is not set up")
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

  def testModuleIsNull(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new ScalaSdkNode(Version("2.11.5"), "", Seq.empty, Seq.empty)
    }.build.toDataNode

    importProjectData(testProject)
  }
}
