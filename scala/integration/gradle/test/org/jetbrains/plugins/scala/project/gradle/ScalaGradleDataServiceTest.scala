package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.model.data.{ScalaCompileOptionsData, ScalaModelData}
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.compiler.data.DebuggingInfoLevel
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._
import org.jetbrains.sbt.project.data.service.ProjectDataServiceTestCase
import org.junit.Assert._

import java.io.File
import java.net.URI
import java.util
import scala.jdk.CollectionConverters._

class ScalaGradleDataServiceTest extends ProjectDataServiceTestCase {

  private def generateProject(scalaVersion: Option[String] = None,
                              scalaCompilerClasspath: Set[File] = Set.empty,
                              compilerOptions: Option[ScalaCompileOptionsData] = None,
                              separateModules: Boolean = true): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val scalaLibrary: Option[library] = scalaVersion.map { version =>
        new library { name := "org.scala-lang:scala-library:" + version }
      }

      scalaLibrary.foreach(libraries += _)

      val myProjectURI: URI = new File(getProject.getBasePath).getCanonicalFile.toURI

      modules += new javaModule {
        val moduleName = "module"
        name := moduleName
        projectId := moduleName
        projectURI := myProjectURI
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

        if (!separateModules) {
          scalaLibrary.foreach(libraryDependencies += _)
        }
      }

      if (separateModules) {
        val productionModule: javaModule = new javaModule {
          val moduleName = "module_main"
          name := moduleName
          projectId := moduleName
          projectURI := myProjectURI
          moduleFileDirectoryPath := getProject.getBasePath + "/module"
          externalConfigPath := getProject.getBasePath + "/module"

          scalaLibrary.foreach(libraryDependencies += _)
        }

        modules += productionModule

        modules += new javaModule {
          val moduleName = "module_test"
          name := moduleName
          projectId := moduleName
          projectURI := myProjectURI
          moduleFileDirectoryPath := getProject.getBasePath + "/module"
          externalConfigPath := getProject.getBasePath + "/module"

          moduleDependencies += productionModule
        }
      }
    }.build.toDataNode

  def testEmptyScalaCompilerClasspath(): Unit = {
    importProjectData(generateProject())
    assertScalaLibraryWarningNotificationShown(getProject)
  }

  def testScalaCompilerClasspathWithoutScala(): Unit = {
    importProjectData(
      generateProject(scalaCompilerClasspath = Set(new File("/tmp/test/not-a-scala-library.jar")))
    )
    assertScalaLibraryWarningNotificationShown(getProject)
  }

  def testWithoutScalaLibrary(): Unit = {
    importProjectData(
      generateProject(scalaCompilerClasspath = defaultCompilerClasspath)
    )
    // TODO: Should we show notification if there no scala library?
    //  Need to understand how would a real Gradle project look like in such case,
    //  when `ScalaModelData` is reported for module without scala library, is it even possible?
    //assertScalaLibraryWarningNotificationShown(getProject)
  }

  def testWithDifferentVersionOfScalaLibrary(): Unit = {
    importProjectData(
      generateProject(Some("2.11.5"), defaultCompilerClasspath)
    )

    assertScalaLibraryWarningNotificationShown(getProject)
  }

  private def assertScalaLibraryWarningNotificationShown(project: Project): Unit = {
    assertScalaLibraryWarningNotificationShown(project, GradleConstants.SYSTEM_ID)
  }

  def testWithTheSameVersionOfScalaLibrary(): Unit = {
    importProjectData(
      generateProject(Some("2.10.4"), defaultCompilerClasspath)
    )

    assertHasScalaSdk()

    // TODO test Scala SDK dependency
  }

  def testCompondModule(): Unit = {
    val options = new ScalaCompileOptionsData()
    options.setAdditionalParameters(util.Arrays.asList("-custom-option"))

    importProjectData(
      generateProject(
        Some("2.10.4"),
        defaultCompilerClasspath,
        Some(options),
        separateModules = false
      )
    )

    assertHasScalaSdk()

    val compilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module")
      ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(module)
    }

    assertTrue("Scala compiler options must be set",
      compilerConfiguration.additionalCompilerOptions.contains("-custom-option"))

    // TODO test Scala SDK dependency
  }

  def testCompilerOptionsSetup(): Unit = {
    val additionalOptions = Seq(
      "-Xplugin:test-plugin1.jar;test-plugin2.jar",
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

    importProjectData(
      generateProject(
        Some("2.10.4"),
        defaultCompilerClasspath,
        Some(options)
      )
    )

    val compilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module_main")
      ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(module)
    }

    def toProjectAbsolutePath(relativePath: String): String =
      new File(getProject.getBasePath).getAbsolutePath + File.separator + relativePath

    assertEquals("debugging info level", DebuggingInfoLevel.Source, compilerConfiguration.debuggingInfoLevel)
    assertCollectionEquals("plugins", Seq("test-plugin1.jar", "test-plugin2.jar").map(toProjectAbsolutePath), compilerConfiguration.plugins)
    assertCollectionEquals("additional compiler options", Seq("-encoding", "utf-8", "-target:jvm-1.5"), compilerConfiguration.additionalCompilerOptions)
    assertTrue("experimental", compilerConfiguration.experimental)
    assertTrue("continuations", compilerConfiguration.continuations)
    assertTrue("deprecationWarnings", compilerConfiguration.deprecationWarnings)
    assertTrue("dynamics", compilerConfiguration.dynamics)
    assertTrue("existentials", compilerConfiguration.existentials)
    assertTrue("explainTypeErrors", compilerConfiguration.explainTypeErrors)
    assertTrue("featureWarnings", compilerConfiguration.featureWarnings)
    assertTrue("higherKinds", compilerConfiguration.higherKinds)
    assertTrue("implicitConversions", compilerConfiguration.implicitConversions)
    assertTrue("macros", compilerConfiguration.macros)
    assertTrue("optimiseBytecode", compilerConfiguration.optimiseBytecode)
    assertTrue("postfixOps", compilerConfiguration.postfixOps)
    assertTrue("reflectiveCalls", compilerConfiguration.reflectiveCalls)
    assertFalse("specialization", compilerConfiguration.specialization)
    assertTrue("uncheckedWarnings", compilerConfiguration.uncheckedWarnings)
    assertFalse("warnings", compilerConfiguration.warnings)

    val testCompilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module_test")
      ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(module)
    }

    assertCollectionEquals("additional compiler options (tests) ", Seq("-encoding", "utf-8", "-target:jvm-1.5"), testCompilerConfiguration.additionalCompilerOptions)
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

  private def defaultCompilerClasspath = Set(new File("/tmp/test/scala-library-2.10.4.jar"))

  private def assertHasScalaSdk(): Unit = {
    val libraries = getProject
      .libraries
      .filter(_.getName.contains("scala-library"))

    assertTrue(
      "Scala SDK must be present",
      libraries.exists(_.isScalaSdk)
    )
  }
}
