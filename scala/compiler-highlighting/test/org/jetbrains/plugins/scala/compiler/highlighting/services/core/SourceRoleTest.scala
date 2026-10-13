package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.{LanguageFileType, PlainTextFileType}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.SourceRole
import org.jetbrains.plugins.scala.finder.ScalaLanguageDerivative
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.Collections

@RunWith(classOf[JUnit4])
class SourceRoleTest extends CompilationStateWritesTestBase:

  private def roleOf(file: VirtualFile): SourceRole = SourceRole.of(getProject, file)

  @Test
  def testScalaAndJavaUnderASourceRootAreCompiled(): Unit =
    assertEquals(SourceRole.Compiled, roleOf(inSources("Compiled.scala")))
    assertEquals(SourceRole.Compiled, roleOf(inSources("Compiled.java")))
    
  @Test
  def testExtensionsAreMatchedCaseInsensitively(): Unit =
    assertEquals(SourceRole.Compiled, roleOf(inSources("Upper.SCALA")))
    assertEquals(SourceRole.Compiled, roleOf(inSources("Test.Java")))
    
  @Test
  def testOtherJvmLanguagesAreBuildInputs(): Unit =
    assertEquals(SourceRole.BuildInput, roleOf(inSources("Kotlin.kt")))
    assertEquals(SourceRole.BuildInput, roleOf(inSources("Script.kts")))
    assertEquals(SourceRole.BuildInput, roleOf(inSources("Groovy.groovy")))

  @Test
  def testWorksheetsAndSbtFilesAreUntracked(): Unit =
    assertEquals(SourceRole.Untracked, roleOf(inSources("Sheet.sc")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("build.sbt")))

  @Test
  def testUnrelatedFileTypesUnderASourceRootAreUntracked(): Unit =
    assertEquals(SourceRole.Untracked, roleOf(inSources("notes.txt")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("script.py")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("native.c")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("config.xml")))

  @Test
  def testResourcesAreUntracked(): Unit =
    assertEquals(SourceRole.Untracked, roleOf(inResources("reference.conf")))
    assertEquals(SourceRole.Untracked, roleOf(inResources("messages.properties")))

  @Test
  def testFilesOutsideEverySourceRootAreUntracked(): Unit =
    assertEquals(SourceRole.Untracked, roleOf(outsideRoots("Detached.scala")))
    assertEquals(SourceRole.Untracked, roleOf(outsideRoots("Detached.java")))

  @Test
  def testAFileTypeThatGeneratesScalaIsABuildInput(): Unit =
    val underSources = inSources("generated.txt")
    assertEquals(SourceRole.Untracked, roleOf(underSources))

    registerScalaDerivative()

    assertEquals(SourceRole.BuildInput, roleOf(underSources))
    assertEquals("outside the sources it still does not matter",
      SourceRole.Untracked, roleOf(outsideRoots("generated.txt")))

  /**
   * Play's Twirl templates reach [[SourceRole]] through this extension point and are registered by pattern
   * rather than by extension; the Play module is not loaded here, so a plain-text derivative stands in for
   * one.
   */
  private def registerScalaDerivative(): Unit =
    val derivative = new ScalaLanguageDerivative:
      override protected def getFileType: LanguageFileType = PlainTextFileType.INSTANCE

    ExtensionTestUtil.maskExtensions(
      ExtensionPointName.create[ScalaLanguageDerivative]("org.intellij.scala.scalaLanguageDerivative"),
      Collections.singletonList(derivative),
      getTestRootDisposable
    )