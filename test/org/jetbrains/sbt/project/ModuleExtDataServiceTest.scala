package org.jetbrains.sbt.project

import java.io.File

import com.intellij.openapi.externalSystem.model.{ExternalSystemException, DataNode}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import org.jetbrains.plugins.scala.project.{DebuggingInfoLevel, CompileOrder, Version}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.sbt.UsefulTestCaseHelper
import org.jetbrains.sbt.project.data.ModuleExtNode

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
class ModuleExtDataServiceTest extends ProjectDataServiceTestCase with UsefulTestCaseHelper {

  import ExternalSystemDsl._

  private def generateProject(scalaVersion: String, scalaLibraryVersion: Option[String], compilerOptions: Seq[String]): DataNode[ProjectData] =
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
        arbitraryNodes += new ModuleExtNode(Some(Version(scalaVersion)), Seq.empty, compilerOptions, None, Seq.empty)
      }
    }.build.toDataNode

  private def doTestAndCheckScalaSdk(scalaVersion: String, scalaLibraryVersion: String): Unit = {
    import org.jetbrains.plugins.scala.project._
    importProjectData(generateProject(scalaVersion, Some(scalaLibraryVersion), Seq.empty))
    val isLibrarySetUp = ProjectLibraryTable.getInstance(getProject).getLibraries.filter(_.getName.contains("scala-library")).exists(_.isScalaSdk)
    assert(isLibrarySetUp, "Scala library is not set up")
  }

  def testWithoutScalaLibrary(): Unit =
    importProjectData(generateProject("2.11.5", None, Seq.empty))

  def testWithIncompatibleScalaLibrary(): Unit =
    assertException[ExternalSystemException](Some("Cannot find project Scala library 2.11.5 for module Module 1")) {
      importProjectData(generateProject("2.11.5", Some("2.10.4"), Seq.empty))
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

  def testCompilerOptionsSetup(): Unit = {
    val options = Seq(
      "-g:source",
      "-Xplugin:test-plugin.jar",
      "-Xexperimental",
      "-P:continuations:enable",
      "-deprecation",
      "-language:dynamics",
      "-language:existentials",
      "-explaintypes",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:macros",
      "-optimise",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      "-no-specialization",
      "-unchecked",
      "-nowarn",
      "-XmyCoolAdditionalOption"
    )

    importProjectData(generateProject("2.11.5", Some("2.11.5"), options))
    val module = ModuleManager.getInstance(getProject).findModuleByName("Module 1")
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(module)

    assert(compilerConfiguration.debuggingInfoLevel == DebuggingInfoLevel.Source)
    assert(compilerConfiguration.plugins == Seq("test-plugin.jar"))
    assert(compilerConfiguration.additionalCompilerOptions == Seq("-XmyCoolAdditionalOption"))
    assert(compilerConfiguration.continuations)
    assert(compilerConfiguration.experimental)
    assert(compilerConfiguration.deprecationWarnings)
    assert(compilerConfiguration.dynamics)
    assert(compilerConfiguration.existentials)
    assert(compilerConfiguration.explainTypeErrors)
    assert(compilerConfiguration.featureWarnings)
    assert(compilerConfiguration.higherKinds)
    assert(compilerConfiguration.implicitConversions)
    assert(compilerConfiguration.macros)
    assert(compilerConfiguration.optimiseBytecode)
    assert(compilerConfiguration.postfixOps)
    assert(compilerConfiguration.reflectiveCalls)
    assert(!compilerConfiguration.specialization)
    assert(compilerConfiguration.uncheckedWarnings)
    assert(!compilerConfiguration.warnings)
  }

  def testModuleIsNull(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new ModuleExtNode(Some(Version("2.11.5")), Seq.empty, Seq.empty, None, Seq.empty)
    }.build.toDataNode

    importProjectData(testProject)
  }
}
