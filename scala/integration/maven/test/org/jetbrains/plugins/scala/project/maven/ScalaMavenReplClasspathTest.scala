package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt, ReplClasspath}
import org.jetbrains.sbt.project.ProjectStructureDsl.project
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertTrue}
import org.junit.jupiter.api.{Test, TestInfo}

/**
 * Tests the resolution of the Scala REPL classpath during Maven import.
 * Kept out of the [[ScalaMavenImporterTest]] JDK matrix because the REPL classpath does not depend on
 * the project JDK.
 */
class ScalaMavenReplClasspathTest extends ScalaMavenImporterTestBase(None):

  @Test
  def resolveReplClasspath_Scala37(using TestInfo): Unit =
    runImportingTest(project(getProject.getName))

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull(scalaSdk, "Scala SDK not configured")

    val properties = scalaSdk.asInstanceOf[LibraryEx].properties

    val replClasspath = properties.replClasspath
    assertEquals(ReplClasspath.Bundled, replClasspath, "The REPL classpath was not configured correctly")

  @Test
  def resolveReplClasspath_Scala38(using TestInfo): Unit =
    runImportingTest(project(getProject.getName))

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull(scalaSdk, "Scala SDK not configured")

    val properties = scalaSdk.asInstanceOf[LibraryEx].properties

    val replClasspath = properties.replClasspath

    assertTrue(replClasspath.isInstanceOf[ReplClasspath.Provided], "The REPL classpath was not configured correctly")
    val paths = replClasspath.asPaths
    assertTrue(paths.nonEmpty, "The REPL classpath is empty")
    val scala3ReplJar = paths.find(_.getFileName.toString == "scala3-repl_3-3.8.0-RC1.jar")
    assertTrue(scala3ReplJar.isDefined, "Could not find the scala3-repl_3 jar on the REPL classpath")
