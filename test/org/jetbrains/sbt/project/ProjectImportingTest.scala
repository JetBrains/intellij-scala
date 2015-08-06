package org.jetbrains.sbt
package project

import java.io.File

import ProjectStructureDsl._
import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class ProjectImportingTest extends ImportingTestCase with InexactMatch {

  def ivyCacheDir: File = new File(System.getProperty("user.home")) / ".ivy2" / "cache"

  def testSimple() = runTest(
    new project("testSimple") {
      lazy val scalaLibrary = new library("SBT: org.scala-lang:scala-library:2.11.6:jar") {
        classes += (ivyCacheDir / "org.scala-lang" / "scala-library" / "jars" / "scala-library-2.11.6.jar").getAbsolutePath
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

  def testMultiModule() = runTest(
    new project("testMultiModule") {
      lazy val foo = new module("foo") {
        moduleDependencies += new dependency(bar) {
          isExported := true
        }
      }

      lazy val bar  = new module("bar")
      lazy val root = new module("multiModule")

      modules := Seq(root, foo, bar)
    })

  def testUnmanagedDependency() = runTest(
    new project("testUnmanagedDependency") {
      modules += new module("unmanagedDependency") {
        lazy val unmanagedLibrary = new library("SBT: unmanaged-jars") {
          classes += (testProjectDir / "lib" / "unmanaged.jar").getAbsolutePath
        }
        libraries += unmanagedLibrary
        libraryDependencies += unmanagedLibrary
      }
    }
  )
}
