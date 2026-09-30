package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.{LanguageFileType, PlainTextFileType}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import org.jetbrains.plugins.scala.CompilerHighlightingTests
import org.jetbrains.plugins.scala.finder.ScalaLanguageDerivative
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

import java.util.Collections

@Category(Array(classOf[CompilerHighlightingTests]))
class SourceRoleTest extends CompilationStateWritesTestBase {

  private def roleOf(file: VirtualFile): SourceRole = SourceRole.of(getProject, file)

  def testScalaAndJavaUnderASourceRootAreCompiled(): Unit = {
    assertEquals(SourceRole.Compiled, roleOf(inSources("Compiled.scala")))
    assertEquals(SourceRole.Compiled, roleOf(inSources("Compiled.java")))
  }

  def testExtensionsAreMatchedCaseInsensitively(): Unit = {
    assertEquals(SourceRole.Compiled, roleOf(inSources("Upper.SCALA")))
    assertEquals(SourceRole.Compiled, roleOf(inSources("Test.Java")))
  }

  def testOtherJvmLanguagesAreBuildInputs(): Unit = {
    assertEquals(SourceRole.BuildInput, roleOf(inSources("Kotlin.kt")))
    assertEquals(SourceRole.BuildInput, roleOf(inSources("Script.kts")))
    assertEquals(SourceRole.BuildInput, roleOf(inSources("Groovy.groovy")))
  }

  def testWorksheetsAndSbtFilesAreUntracked(): Unit = {
    assertEquals(SourceRole.Untracked, roleOf(inSources("Sheet.sc")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("build.sbt")))
  }

  def testUnrelatedFileTypesUnderASourceRootAreUntracked(): Unit = {
    assertEquals(SourceRole.Untracked, roleOf(inSources("notes.txt")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("script.py")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("native.c")))
    assertEquals(SourceRole.Untracked, roleOf(inSources("config.xml")))
  }

  def testResourcesAreUntracked(): Unit = {
    assertEquals(SourceRole.Untracked, roleOf(inResources("reference.conf")))
    assertEquals(SourceRole.Untracked, roleOf(inResources("messages.properties")))
  }

  def testFilesOutsideEverySourceRootAreUntracked(): Unit = {
    assertEquals(SourceRole.Untracked, roleOf(outsideRoots("Detached.scala")))
    assertEquals(SourceRole.Untracked, roleOf(outsideRoots("Detached.java")))
  }

  def testAFileTypeThatGeneratesScalaIsABuildInput(): Unit = {
    val underSources = inSources("generated.txt")
    assertEquals(SourceRole.Untracked, roleOf(underSources))

    registerScalaDerivative()

    assertEquals(SourceRole.BuildInput, roleOf(underSources))
    assertEquals("outside the sources it still does not matter",
      SourceRole.Untracked, roleOf(outsideRoots("generated.txt")))
  }

  /**
   * Play's Twirl templates reach [[SourceRole]] through this extension point and are registered by pattern
   * rather than by extension; the Play module is not loaded here, so a plain-text derivative stands in for
   * one.
   */
  private def registerScalaDerivative(): Unit = {
    val derivative = new ScalaLanguageDerivative {
      override protected def getFileType: LanguageFileType = PlainTextFileType.INSTANCE
    }
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName.create[ScalaLanguageDerivative]("org.intellij.scala.scalaLanguageDerivative"),
      Collections.singletonList(derivative),
      getTestRootDisposable
    )
  }
}
