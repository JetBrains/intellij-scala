package org.jetbrains.sbt.project

import org.jetbrains.sbt.project.ProjectStructureDsl.*

object ProjectStructureDslTestUtils {

  def commonSourceResourceAndTargetDirs(module: module): Unit = {
    import module.*
    sources := Seq("src/main/scala", "src/main/java")
    testSources := Seq("src/test/scala", "src/test/java")
    resources := Seq("src/main/resources")
    testResources := Seq("src/test/resources")
    excluded := Seq("target")
  }

  def emptySourceResourceDirs(module: module): Unit = {
    emptySourceResourceDirsMain(module)
    emptySourceResourceDirsTest(module)
  }

  def emptySourceResourceDirsMain(module: module): Unit = {
    import module.*
    sources := Nil
    resources := Nil
  }

  def emptySourceResourceDirsTest(module: module): Unit = {
    import module.*
    testSources := Nil
    testResources := Nil
  }

  def createModuleWithSourceSet(moduleName: String, group: Array[String] = null): Seq[module] =
    Seq(moduleName, s"$moduleName.main", s"$moduleName.test").map { name =>
      new module(name, group)
    }

  def standardRoots(relativePath: String, scope: String, scalaVersion: String = "2.13"): Seq[String] = {
    val normalized = if (relativePath.isEmpty) "" else s"$relativePath/"
    Seq(
      s"%PROJECT_ROOT%/${normalized}src/$scope",
      s"%PROJECT_ROOT%/${normalized}target/scala-$scalaVersion/src_managed/$scope",
      s"%PROJECT_ROOT%/${normalized}target/scala-$scalaVersion/resource_managed/$scope"
    )
  }
}
