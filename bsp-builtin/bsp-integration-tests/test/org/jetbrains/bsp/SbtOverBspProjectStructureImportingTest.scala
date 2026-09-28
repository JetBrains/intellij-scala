package org.jetbrains.bsp

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt, ReplClasspath}
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, excluded, libraries, libraryDependencies, module, modules, project, resources, sources, testResources, testSources}
import org.jetbrains.sbt.project.RequiresJdk
import org.junit.Assert.{assertEquals, assertNotEquals, assertTrue}
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
class SbtOverBspProjectStructureImportingTest extends SbtOverBspProjectStructureImportingTestBase {

  def testSimple(): Unit = {
    importProject(false)

    val scalaLibraries = BspProjectStructureImportingTestUtils.expectedScalaLibraryWithScalaSdk("2.13.14", useScalaSdkExtraClasspath = true)

    val expectedProject = new project("simple") {
      libraries := scalaLibraries
      libraries.inexactMatch()

      modules := Seq(
        new module("simple") {
          contentRoots := Seq(getProjectPath)
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(scalaLibraries, "simple")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq("target", ".bloop", ".bsp")
        },
        new module("simple-build") {
          contentRoots := Seq(s"$getProjectPath/project")
          sources := Nil
          testSources := Nil
          resources := Nil
          testResources := Nil
          excluded := Nil
        }
      )
    }

    assertProjectsEqual(expectedProject, getMyProject)
  }

  @RequiresJdk(LanguageLevel.JDK_17)
  def testResolveReplClasspath_Scala38(): Unit = {
    importProject(false)

    def replClasspathOf(library: Library): ReplClasspath = library.asInstanceOf[LibraryEx].properties.replClasspath

    val scalaSdks = getMyProject.libraries.filter(_.isScalaSdk)
    val (scala38Sdks, otherScalaSdks) = scalaSdks.partition(_.getName == "BSP: scala-sdk-3.8.0-RC1")
    assertEquals(s"Expected exactly one Scala 3.8 SDK, found: ${scalaSdks.map(_.getName)}", 1, scala38Sdks.size)

    val replClasspath = replClasspathOf(scala38Sdks.head)
    assertTrue("The REPL classpath was not configured correctly", replClasspath.is[ReplClasspath.Provided])
    val replJars = replClasspath.asPaths.map(_.getFileName.toString)
    assertTrue("Could not find the scala3-repl_3 jar on the REPL classpath", replJars.contains("scala3-repl_3-3.8.0-RC1.jar"))

    // The sbt build module has its own Scala SDK for sbt's Scala version. The REPL classpath cache is keyed by
    // Scala version, so that SDK must not receive the Scala 3.8 REPL classpath.
    assertTrue(s"Expected the sbt build module Scala SDK, found: ${scalaSdks.map(_.getName)}", otherScalaSdks.nonEmpty)
    otherScalaSdks.foreach { sdk =>
      assertNotEquals(s"${sdk.getName} received the Scala 3.8 REPL classpath", replClasspath, replClasspathOf(sdk))
    }
  }
}