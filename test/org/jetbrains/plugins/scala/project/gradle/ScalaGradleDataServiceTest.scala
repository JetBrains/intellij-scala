package org.jetbrains.plugins.scala.project.gradle

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.gradle.model.data.{ScalaCompileOptionsData, ScalaModelData}
import org.jetbrains.plugins.scala.project.DebuggingInfoLevel
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.sbt.UsefulTestCaseHelper
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._
import org.jetbrains.sbt.project.data.service.{ExternalSystemDataDsl, ProjectDataServiceTestCase}

import scala.collection.JavaConverters._


/**
 * @author Nikolay Obedin
 * @since 6/4/15.
 */
class ScalaGradleDataServiceTest extends ProjectDataServiceTestCase with UsefulTestCaseHelper {

  private def generateProject(scalaVersion: Option[String], scalaCompilerClasspath: Set[File],
      compilerOptions: Option[ScalaCompileOptionsData]): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val scalaLibrary = scalaVersion.map { version =>
        new library { name := "org.scala-lang:scala-library:" + version }
      }

      scalaLibrary.foreach(libraries += _)

      modules += new javaModule {
        name := "module"
        moduleFileDirectoryPath := getProject.getBasePath + "/module"
        externalConfigPath := getProject.getBasePath + "/module"

        arbitraryNodes += new Node[ScalaModelData] {
          override protected val data: ScalaModelData = new ScalaModelData(SbtProjectSystem.Id)
          override protected def key: Key[ScalaModelData] = ScalaModelData.KEY

          def asSerializableJavaSet[T](scalaSet: Set[T]): util.Set[T] = {
            val classpath = new util.HashSet[T]()
            util.Collections.addAll(classpath, scalaSet.toSeq:_*)
            classpath
          }

          data.setScalaClasspath(asSerializableJavaSet(scalaCompilerClasspath))
          data.setScalaCompileOptions(compilerOptions.getOrElse(new ScalaCompileOptionsData))
          data.setTargetCompatibility("1.5")
        }
      }

      private val productionModule = new javaModule {
        name := "module_main"
        moduleFileDirectoryPath := getProject.getBasePath + "/module"
        externalConfigPath := getProject.getBasePath + "/module"

        scalaLibrary.foreach(libraryDependencies += _)
      }

      modules += productionModule

      modules += new javaModule {
        name := "module_test"
        moduleFileDirectoryPath := getProject.getBasePath + "/module"
        externalConfigPath := getProject.getBasePath + "/module"

        moduleDependencies += productionModule
      }
    }.build.toDataNode

  def testEmptyScalaCompilerClasspath(): Unit = {
    importProjectData(generateProject(None, Set.empty, None))
    // FIXME: can't check notification count for Gradle because tool window is uninitialized
    // assertNotificationsCount(NotificationSource.PROJECT_SYNC, NotificationCategory.WARNING, GradleConstants.SYSTEM_ID, 1)
  }

  def testScalaCompilerClasspathWithoutScala(): Unit = {
    importProjectData(generateProject(None, Set(new File("/tmp/test/not-a-scala-library.jar")), None))
    // FIXME: can't check notification count for Gradle because tool window is uninitialized
    // assertNotificationsCount(NotificationSource.PROJECT_SYNC, NotificationCategory.WARNING, GradleConstants.SYSTEM_ID, 1)
  }

  def testWithoutScalaLibrary(): Unit = {
    importProjectData(generateProject(None, Set(new File("/tmp/test/scala-library-2.10.4.jar")), None))
    // FIXME: can't check notification count for Gradle because tool window is uninitialized
    // assertNotificationsCount(NotificationSource.PROJECT_SYNC, NotificationCategory.WARNING, GradleConstants.SYSTEM_ID, 1)
  }

  def testWithDifferentVersionOfScalaLibrary(): Unit = {
    importProjectData(generateProject(Some("2.11.5"), Set(new File("/tmp/test/scala-library-2.10.4.jar")), None))
    // FIXME: can't check notification count for Gradle because tool window is uninitialized
    // assertNotificationsCount(NotificationSource.PROJECT_SYNC, NotificationCategory.WARNING, GradleConstants.SYSTEM_ID, 1)
  }

  def testWithTheSameVersionOfScalaLibrary(): Unit = {
    importProjectData(generateProject(Some("2.10.4"), Set(new File("/tmp/test/scala-library-2.10.4.jar")), None))

    import org.jetbrains.plugins.scala.project._
    val isLibrarySetUp = getProject.libraries
      .filter(_.getName.contains("scala-library"))
      .exists(_.isScalaSdk)
    assert(isLibrarySetUp, "Scala library is not set up")
  }

  def testCompilerOptionsSetup(): Unit = {
    val additionalOptions = Seq(
      "-Xplugin:test-plugin.jar",
      "-Xexperimental",
      "-P:continuations:enable",
      "-language:dynamics",
      "-language:existentials",
      "-explaintypes",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:macros",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      "-no-specialization",
      "-nowarn"
    )

    val options = new ScalaCompileOptionsData
    options.setDebugLevel("source")
    options.setEncoding("utf-8")
    options.setDeprecation(true)
    options.setOptimize(true)
    options.setUnchecked(true)
    options.setAdditionalParameters(additionalOptions.asJava)

    importProjectData(generateProject(Some("2.10.4"), Set(new File("/tmp/test/scala-library-2.10.4.jar")), Some(options)))

    val compilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module_main")
      ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(module)
    }

    assert(compilerConfiguration.debuggingInfoLevel == DebuggingInfoLevel.Source)
    assert(compilerConfiguration.plugins == Seq("test-plugin.jar"))
    assert(compilerConfiguration.additionalCompilerOptions == Seq("-encoding", "utf-8", "-target:jvm-1.5"))
    assert(compilerConfiguration.experimental)
    assert(compilerConfiguration.continuations)
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

    val testCompilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module_test")
      ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(module)
    }

    assert(testCompilerConfiguration.additionalCompilerOptions == Seq("-encoding", "utf-8", "-target:jvm-1.5"))
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
