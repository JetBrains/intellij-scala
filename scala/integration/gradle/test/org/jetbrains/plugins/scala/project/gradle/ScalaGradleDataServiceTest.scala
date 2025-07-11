package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.module.{ModuleManager, StdModuleTypes}
import org.jetbrains.plugins.gradle.model.data.{ScalaCompileOptionsData, ScalaModelData}
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.compiler.data.DebuggingInfoLevel
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings.ScalacPlugin
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._
import org.jetbrains.sbt.project.data.service.ProjectDataServiceTestCase
import org.junit.Assert._

import java.net.URI
import java.nio.file.Path
import java.util
import scala.jdk.CollectionConverters._

class ScalaGradleDataServiceTest extends ProjectDataServiceTestCase {

  // Synthetic module type to distinguish source sets modules (main/test) from regular modules
  private val sourceSetModuleType: String = "source_set"

  private def generateProject(
    scalaVersion: Option[String] = None,
    scalaCompilerClasspath: Set[Path] = Set.empty,
    scalaCompilerPlugins: Set[Path] = Set.empty,
    compilerOptions: Option[ScalaCompileOptionsData] = None,
    separateModules: Boolean = true,
    addScalaLibrariesModuleLevel: Boolean = false
  ): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val scalaLibrary: Option[library] = scalaVersion.map { version =>
        new library { name := "org.scala-lang:scala-library:" + version }
      }

      if (!addScalaLibrariesModuleLevel) {
        scalaLibrary.foreach(libraries += _)
      }

      val myProjectURI: URI = Path.of(getProject.getBasePath).toAbsolutePath.toUri

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

          data.setScalaClasspath(asSerializableJavaSet(scalaCompilerClasspath.map(_.toFile)))
          data.setScalaCompilerPlugins(asSerializableJavaSet(scalaCompilerPlugins.map(_.toFile)))
          data.setScalaCompileOptions(compilerOptions.getOrElse(new ScalaCompileOptionsData))
        }

        if (!separateModules) {
          scalaLibrary.foreach(libraryDependencies += _)
        }
      }

      if (separateModules) {
        val productionModule: javaModule = new javaModule {
          override val typeId: String = sourceSetModuleType
          val moduleName = "module_main"
          name := moduleName
          projectId := moduleName
          projectURI := myProjectURI
          moduleFileDirectoryPath := getProject.getBasePath + "/module"
          externalConfigPath := getProject.getBasePath + "/module"

          // Libraries declared at the module level are added as module-level dependencies,
          // whereas libraryDependencies are added as project-level dependencies.
          if (addScalaLibrariesModuleLevel) {
            scalaLibrary.foreach(libraries += _)
          } else {
            scalaLibrary.foreach(libraryDependencies += _)
          }
        }

        modules += productionModule

        modules += new javaModule {
          override val typeId: String = sourceSetModuleType
          val moduleName = "module_test"
          name := moduleName
          projectId := moduleName
          projectURI := myProjectURI
          moduleFileDirectoryPath := getProject.getBasePath + "/module"
          externalConfigPath := getProject.getBasePath + "/module"

          moduleDependencies += productionModule
          // Libraries declared at the module level are added as module-level dependencies,
          // whereas libraryDependencies are added as project-level dependencies.
          if (addScalaLibrariesModuleLevel) {
            scalaLibrary.foreach(libraries += _)
          } else {
            scalaLibrary.foreach(libraryDependencies += _)
          }
        }
      }
    }.build.toDataNode

  def testEmptyScalaCompilerClasspath(): Unit = {
    importProjectData(generateProject())
    assertScalaLibraryWarningNotificationShown(numberOfNotifications = 2)
  }

  def testScalaCompilerClasspathWithoutScala(): Unit = {
    importProjectData(
      generateProject(scalaCompilerClasspath = Set(Path.of("/", "tmp", "test", "not-a-scala-library.jar")))
    )
    assertScalaLibraryWarningNotificationShown(numberOfNotifications = 2)
  }

  // In a real Gradle project, if there is no scala library, a built-in exception is thrown (likely from the Scala Gradle plugin).
  // However, in this case, we don't perform a Gradle reload process.
  // Since the compiler classpath contains the Scala library, the Scala SDK is created in the data service.
  def testWithoutScalaLibrary(): Unit = {
    importProjectData(
      generateProject(scalaCompilerClasspath = defaultCompilerClasspath)
    )
    assertHasScalaSdkSeparateModules()
  }

  private def assertScalaLibraryWarningNotificationShown(numberOfNotifications: Int): Unit = {
    assertScalaLibraryWarningNotificationShown(getProject, GradleConstants.SYSTEM_ID, numberOfNotifications)
  }

  def testWithTheSameVersionOfScalaLibrary(): Unit = {
    importProjectData(
      generateProject(Some("2.10.4"), defaultCompilerClasspath)
    )

    assertHasScalaSdkSeparateModules()
  }

  def testWithTheSameVersionOfScalaLibrary_ModuleLevel(): Unit = {
    importProjectData(
      generateProject(
        scalaVersion = Some("2.10.4"),
        scalaCompilerClasspath = defaultCompilerClasspath,
        addScalaLibrariesModuleLevel = true
      )
    )

    assertHasScalaSdkSeparateModules()
  }

  def testCompondModule(): Unit = {
    val options = new ScalaCompileOptionsData()
    options.setAdditionalParameters(util.Arrays.asList("-custom-option"))

    importProjectData(
      generateProject(
        Some("2.10.4"),
        defaultCompilerClasspath,
        Set.empty,
        Some(options),
        separateModules = false
      )
    )

    assertHasScalaSdkCompondModules()

    val compilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module")
      ScalaCompilerSettings.forModule(module)
    }

    assertTrue("Scala compiler options must be set",
      compilerConfiguration.additionalCompilerOptions.contains("-custom-option"))

    // TODO test Scala SDK dependency
  }

  def testCompilerOptionsSetup(): Unit = {
    val additionalOptions = Seq(
      "-Xplugin:test-plugin1.jar,test-plugin2.jar",
      "-Xexperimental",
      "-P:continuations:enable",
      "-language:dynamics",
      "-language:existentials",
      "-explaintypes",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:experimental.macros",
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
        Set.empty,
        Some(options)
      )
    )

    val compilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module_main")
      ScalaCompilerSettings.forModule(module)
    }

    def toProjectAbsolutePath(relativePath: String): String =
      Path.of(getProject.getBasePath).toAbsolutePath.resolve(relativePath).toString

    assertEquals("debugging info level", DebuggingInfoLevel.Source, compilerConfiguration.debuggingInfoLevel)

    val pluginPaths = Seq("test-plugin1.jar", "test-plugin2.jar").map(toProjectAbsolutePath)
    assertCollectionEquals("plugins", pluginPaths.map(ScalacPlugin.fromClasspath), compilerConfiguration.plugins)
    assertCollectionEquals("additional compiler options", Seq("-encoding", "utf-8"), compilerConfiguration.additionalCompilerOptions)
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
      ScalaCompilerSettings.forModule(module)
    }

    assertCollectionEquals("additional compiler options (tests) ", Seq("-encoding", "utf-8"), testCompilerConfiguration.additionalCompilerOptions)
  }

  def testScalaCompilerPlugins(): Unit = {
    val scalaLibraryPath = Path.of("/", "tmp", "test", "scala-library-2.13.14.jar")
    val scalacPluginPath = Path.of("/", "tmp", "test", "scalac-plugin_2.13-1.0.0.jar")

    importProjectData(
      generateProject(
        Some("2.13.14"),
        Set(scalaLibraryPath),
        Set(scalacPluginPath)
      )
    )

    val compilerConfiguration = {
      val module = ModuleManager.getInstance(getProject).findModuleByName("module_main")
      ScalaCompilerSettings.forModule(module)
    }

    assertCollectionEquals(Seq(ScalacPlugin.fromClasspath(scalacPluginPath.toString)), compilerConfiguration.plugins)
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

  private def defaultCompilerClasspath = Set(Path.of("/", "tmp", "test", "scala-library-2.10.4.jar"))

  private def assertHasScalaSdkSeparateModules(): Unit =
    assertHasScalaSdk(separateModules = true, expectedScalaSdkModulesCount = 2)

  private def assertHasScalaSdkCompondModules(): Unit =
    assertHasScalaSdk(separateModules = false, expectedScalaSdkModulesCount = 1)

  private def assertHasScalaSdk(separateModules: Boolean, expectedScalaSdkModulesCount: Int): Unit = {
    val allModules = ModuleManager.getInstance(getProject).getModules
    val filterType =
      if (separateModules) sourceSetModuleType
      else StdModuleTypes.JAVA.getId

    val modules = allModules.filter(module => Option(module.getModuleTypeName).contains(filterType))
    assertEquals(s"There should be $expectedScalaSdkModulesCount modules", expectedScalaSdkModulesCount, modules.length)

    modules.foreach { module =>
      val scalaSdks = module.libraries.filter(_.isScalaSdk)
      assertTrue(s"There is no single Scala SDK in ${module.getName}", scalaSdks.size == 1)
    }
  }
}
