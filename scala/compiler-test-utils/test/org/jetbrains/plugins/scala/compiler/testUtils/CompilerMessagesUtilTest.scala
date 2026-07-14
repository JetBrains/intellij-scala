package org.jetbrains.plugins.scala.compiler.testUtils

import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.{assertEquals, assertFalse, fail}
import org.junit.Test

final class CompilerMessagesUtilTest {

  @Test
  def assertNoErrorsIgnoresWarnings(): Unit =
    CompilerMessagesUtil.assertNoErrors(Seq(message(CompilerMessageCategory.WARNING, "unused import")))

  @Test
  def assertNoErrorsOrWarningsFailsForWarnings(): Unit = {
    val error = assertFails {
      CompilerMessagesUtil.assertNoErrorsOrWarnings(Seq(message(CompilerMessageCategory.WARNING, "unused import")))
    }

    assertFalse(error.getMessage.contains("List("))
    assertEquals(
      s"""Expected no compilation errors or warnings, got:
         |WARNING: unused import""".stripMargin,
      error.getMessage
    )
  }

  @Test
  def assertNoErrorsFailsForErrors(): Unit = {
    val error = assertFails {
      CompilerMessagesUtil.assertNoErrors(Seq(message(CompilerMessageCategory.ERROR, "type mismatch")))
    }

    assertEquals(
      s"""Expected no compilation errors, got:
         |ERROR: type mismatch""".stripMargin,
      error.getMessage
    )
  }

  @Test
  def compilerMessagesTextOmitsFileHeaderForSingleFile(): Unit = {
    val file = new LightVirtualFile("A.scala")
    val text = CompilerMessagesUtil.compilerMessagesText(Seq(
      message(CompilerMessageCategory.ERROR, " first error ", file),
      message(CompilerMessageCategory.WARNING, "warning", file)
    ))

    assertEquals(
      s"""ERROR: first error
         |WARNING: warning""".stripMargin,
      text
    )
  }

  @Test
  def compilerMessagesTextGroupsAndSortsMultipleFiles(): Unit = {
    val a = new LightVirtualFile("A.scala")
    val b = new LightVirtualFile("B.scala")
    val text = CompilerMessagesUtil.compilerMessagesText(Seq(
      message(CompilerMessageCategory.ERROR, "b error", b),
      message(CompilerMessageCategory.WARNING, "a warning", a)
    ))

    assertEquals(
      s"""$a:
         |WARNING: a warning
         |$b:
         |ERROR: b error""".stripMargin,
      text
    )
  }

  private def assertFails(body: => Unit): AssertionError = {
    var assertion: AssertionError = null

    try {
      body
    } catch {
      case error: AssertionError =>
        assertion = error
    }

    if (assertion == null) {
      fail("Expected assertion to fail")
    }

    assertion
  }

  private def message(
    category: CompilerMessageCategory,
    text: String,
    file: VirtualFile = null
  ): CompilerMessage =
    new CompilerMessage {
      override def getCategory: CompilerMessageCategory = category

      override def getMessage: String = text

      override def getNavigatable: Navigatable = null

      override def getVirtualFile: VirtualFile = file

      override def getExportTextPrefix: String = ""

      override def getRenderTextPrefix: String = ""
    }
}
