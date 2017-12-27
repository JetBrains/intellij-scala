package org.jetbrains.sbt
package project

import org.jetbrains.plugins.scala.{DependencyManager, SlowTests}
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.junit.experimental.categories.Category
import org.jetbrains.plugins.scala.DependencyManagerBase._

@Category(Array(classOf[SlowTests]))
class ProjectImportingTest extends ImportingTestCase with InexactMatch {

  private val scalaVersion = org.jetbrains.plugins.scala.debugger.Scala_2_11.minor

  def testSimple(): Unit = runTest(
    new project("simple") {
      lazy val scalaLibrary: library = new library(s"sbt: org.scala-lang:scala-library:$scalaVersion:jar") {
        classes += DependencyManager.resolve("org.scala-lang" % "scala-library" % scalaVersion).head.file.getAbsolutePath
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
}
