package org.jetbrains.plugins.scala.project.gradle

import java.io.File

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException, Key}
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import org.jetbrains.plugins.gradle.model.data.{ScalaCompileOptionsData, ScalaModelData}
import org.jetbrains.sbt.project.ExternalSystemDsl._
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.{ProjectDataServiceTestCase, SbtProjectSystem}

import scala.collection.JavaConverters._


/**
 * @author Nikolay Obedin
 * @since 6/4/15.
 */
class ScalaGradleDataServiceTest extends ProjectDataServiceTestCase {

  def generateProject(scalaVersion: Option[String], scalaCompilerClasspath: Set[File]): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val scalaLibrary = scalaVersion.map { version =>
        new library { name := "org.scala-lang:scala-library:" + version }
      }

      scalaLibrary.foreach(libraries += _)

      modules += new javaModule {
        name := "Module 1"
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"

        scalaLibrary.foreach(libraryDependencies += _)

        arbitraryNodes += new Node[ScalaModelData] {
          override protected val data: ScalaModelData = new ScalaModelData(SbtProjectSystem.Id)
          override protected def key: Key[ScalaModelData] = ScalaModelData.KEY

          data.setScalaClasspath(scalaCompilerClasspath.asJava)
          data.setScalaCompileOptions(new ScalaCompileOptionsData)
          data.setTargetCompatibility("1.5")
        }
      }
    }.build.toDataNode

  val compilerVersionError = Some("Cannot determine Scala compiler version for module Module 1")
  val scalaLibraryError = Some("Cannot find project Scala library 2.10.4 for module Module 1")

  def testEmptyScalaCompilerClasspath(): Unit =
    assertException[ExternalSystemException](compilerVersionError) {
      importProjectData(generateProject(None, Set.empty))
    }

  def testScalaCompilerClasspathWithoutScala(): Unit =
    assertException[ExternalSystemException](compilerVersionError) {
      importProjectData(generateProject(None, Set(new File("/tmp/test/not-a-scala-library.jar"))))
    }

  def testWithoutScalaLibrary(): Unit =
    assertException[ExternalSystemException](scalaLibraryError) {
      importProjectData(generateProject(None, Set(new File("/tmp/test/scala-library-2.10.4.jar"))))
    }

  def testWithDifferentVersionOfScalaLibrary(): Unit =
    assertException[ExternalSystemException](scalaLibraryError) {
      importProjectData(generateProject(Some("2.11.5"), Set(new File("/tmp/test/scala-library-2.10.4.jar"))))
    }

  def testWithTheSameVersionOfScalaLibrary(): Unit = {
    importProjectData(generateProject(Some("2.10.4"), Set(new File("/tmp/test/scala-library-2.10.4.jar"))))

    import org.jetbrains.plugins.scala.project._
    val isLibrarySetUp = ProjectLibraryTable.getInstance(getProject).getLibraries.filter(_.getName.contains("scala-library")).exists(_.isScalaSdk)
    assert(isLibrarySetUp, "Scala library is not set up")
  }

  def testModuleIsNull(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      arbitraryNodes += new Node[ScalaModelData] {
        override protected val data: ScalaModelData = new ScalaModelData(SbtProjectSystem.Id)
        override protected def key: Key[ScalaModelData] = ScalaModelData.KEY
      }
    }.build.toDataNode

    importProjectData(testProject)
  }
}
