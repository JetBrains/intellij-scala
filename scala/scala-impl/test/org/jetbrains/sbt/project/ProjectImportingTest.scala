package org.jetbrains.sbt
package project

import java.io.File
import java.net.URI

import org.jetbrains.plugins.scala.{DependencyManager, DependencyManagerBase, Scala_2_11, SlowTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class ProjectImportingTest extends ImportingTestCase with InexactMatch {

  import DependencyManagerBase._
  import ProjectImportingTest._
  import ProjectStructureDsl._

  implicit private val scalaVersion: Scala_2_11.type = Scala_2_11

  def testSimple(): Unit = runTest(
    new project("simple") {
      lazy val scalaLibrary: library = {
        val dependency = scalaLibraryDescription
        new library(s"sbt: $dependency:jar") {
          classes += DependencyManager.resolveSingle(dependency).file.getAbsolutePath
        }
      }

      libraries += scalaLibrary

      modules += new module("simple") {
        contentRoots += getProjectPath
        ProjectStructureDsl.sources := Seq("src/main/scala", "src/main/java")
        testSources := Seq("src/test/scala", "src/test/java")
        resources := Seq("src/main/resources")
        testResources := Seq("src/test/resources")
        excluded := Seq("target")
        libraryDependencies += scalaLibrary
      }

      modules += new module("simple-build") {
        ProjectStructureDsl.sources := Seq("")
        excluded := Seq("project/target", "target")
      }
    })

  def testMultiModule(): Unit = runTest(
    new project("multiModule") {
      lazy val foo: module = new module("foo") {
        moduleDependencies += new dependency(bar) {
          isExported := true
        }
      }

      lazy val bar  = new module("bar")
      lazy val root = new module("multiModule")

      modules := Seq(root, foo, bar)
    })

  def testUnmanagedDependency(): Unit = runTest(
    new project("unmanagedDependency") {
      modules += new module("unmanagedDependency") {
        lazy val unmanagedLibrary: library = new library("sbt: unmanaged-jars") {
          classes += (testProjectDir / "lib" / "unmanaged.jar").getAbsolutePath
        }
        libraries += unmanagedLibrary
        libraryDependencies += unmanagedLibrary
      }
    }
  )

  def testSharedSources(): Unit = runTest(
    new project("sharedSources") {
      lazy val sharedSourcesModule: module = new module("sharedSources-sources") {
        contentRoots += getProjectPath + "/shared"
        ProjectStructureDsl.sources += "src/main/scala"
      }

      lazy val foo: module = new module("foo") {
        moduleDependencies += sharedSourcesModule
      }

      lazy val bar: module = new module("bar") {
        moduleDependencies += sharedSourcesModule
      }

      modules := Seq(foo, bar, sharedSourcesModule)
    }
  )

  def testExcludedDirectories(): Unit = runTest(
    new project("root") {
      modules += new module("root") {
        excluded := Seq(
          "directory-to-exclude-1",
          "directory/to/exclude/2"
        )
      }
    }
  )

  /**
    * SCL-12520: Generate shared sources module when it is only used form a single other module
    */
  def testSCL12520(): Unit = runTest(
    new project("scl12520") {

      val projectURI: URI = getProjectPath.toURI

      val sharedModule: module = new module("p1-sources") {
        contentRoots += getProjectPath + "/p1/shared"
      }

      val jvmModule: module = new module("p1") {
        moduleDependencies += sharedModule
        contentRoots += getProjectPath + "/p1/jvm"
      }

      val rootModule: module = new module("scl12520") {}
      val rootBuildModule: module = new module("scl12520-build") {}

      modules := Seq(sharedModule, rootModule, rootBuildModule, jvmModule)
    }
  )

  /**
    * SCL-13600: generate all modules when there is a duplicate project id in the sbt build
    * due to references to different builds, or multiple sbt projects being imported independently from IDEA
    */
  def testSCL13600(): Unit = runTest(
    new project("scl13600") {
      val buildURI: URI = new File(getHomePath).getCanonicalFile.toURI
      lazy val base: module = new module("root") {
        sbtBuildURI := buildURI
        sbtProjectId := "root"

        moduleDependencies += new dependency(c1) {
          isExported := true
        }
        moduleDependencies += new dependency(c2) {
          isExported := true
        }
      }

      lazy val c1: module = new module("root") {
        sbtBuildURI := buildURI.resolve("c1")
        sbtProjectId := "root"
      }
      lazy val c2: module = new module("root") {
        sbtBuildURI := buildURI.resolve("c2")
        sbtProjectId := "root"
      }

      modules := Seq(base,c1,c2)
    }
  )

  def testSCL14635(): Unit = runTest(
    new project("SCL-14635") {
      val buildURI: URI = new File(getHomePath).getCanonicalFile.toURI

      lazy val base: module = new module("SCL-14635") {
        sbtBuildURI := buildURI
        sbtProjectId := "SCL-14635"
      }

      lazy val ideaPlugin: module = new module("sbt-idea-plugin") {
        sbtBuildURI := new URI("git://github.com/JetBrains/sbt-idea-plugin")
      }
      lazy val ideaPluginBuild: module = new module("sbt-idea-plugin-build") {}

      lazy val ideaShell: module = new module("sbt-idea-shell") {
        sbtBuildURI := new URI("git://github.com/JetBrains/sbt-idea-shell")
      }
      lazy val ideaShellBuild: module = new module("sbt-idea-shell-build") {}

      lazy val ideSettings: module = new module("sbt-ide-settings") {
        sbtBuildURI := new URI("https://github.com/JetBrains/sbt-ide-settings.git")
      }
      lazy val ideSettingsBuild: module = new module("sbt-ide-settings-build") {}
    }
  )
}

object ProjectImportingTest {
  implicit class StringOps(str: String) {
    def toURI: URI = new File(str).getCanonicalFile.toURI
  }

}
