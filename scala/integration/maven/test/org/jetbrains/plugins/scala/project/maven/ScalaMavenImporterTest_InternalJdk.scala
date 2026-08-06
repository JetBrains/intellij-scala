package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt, ReplClasspath}
import org.jetbrains.sbt.project.ProjectStructureDsl.project
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.Test

class ScalaMavenImporterTest_InternalJdk extends ScalaMavenImporterTest:
  override protected def projectJdkVersion: Option[LanguageLevel] = None

  @Test
  def resolveReplClasspath_Scala37(): Unit =
    runImportingTest(new project("resolveReplClasspath_Scala37"))

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk.asInstanceOf[LibraryEx].properties

    val replClasspath = properties.replClasspath
    assertEquals("The REPL classpath was not configured correctly", ReplClasspath.Bundled, replClasspath)

  @Test
  def resolveReplClasspath_Scala38(): Unit =
    runImportingTest(new project("resolveReplClasspath_Scala38"))

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk.asInstanceOf[LibraryEx].properties

    val replClasspath = properties.replClasspath

    assertTrue("The REPL classpath was not configured correctly", replClasspath.isInstanceOf[ReplClasspath.Provided])
    val paths = replClasspath.asPaths
    assertTrue("The REPL classpath is empty", paths.nonEmpty)
    val scala3ReplJar = paths.find(_.getFileName.toString == "scala3-repl_3-3.8.0-RC1.jar")
    assertTrue("Could not find the scala3-repl_3 jar on the REPL classpath", scala3ReplJar.isDefined)
