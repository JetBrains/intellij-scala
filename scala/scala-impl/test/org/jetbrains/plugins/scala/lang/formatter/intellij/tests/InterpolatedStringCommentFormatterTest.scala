package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.junit.Assert._

class InterpolatedStringCommentFormatterTest extends InterpolatedStringCommentFormatterTestBase

class Scala3InterpolatedStringCommentFormatterTest extends InterpolatedStringCommentFormatterTestBase {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_5
}

abstract class InterpolatedStringCommentFormatterTestBase extends AbstractScalaFormatterTestBase {
  override protected def setUp(): Unit = {
    super.setUp()
    getIndentOptions.INDENT_SIZE = 4
    getIndentOptions.CONTINUATION_INDENT_SIZE = 4
  }

  private def checkCommentInInjection(injection: String): Unit = {
    val opening = if (version.isScala3) "object Test:" else "object Test {"
    val method =
      if (version.isScala3)
        """    def f(n: Int): Int =
          |        if n > 0 then
          |            n + 1
          |        else
          |            0""".stripMargin
      else
        """    def f: Int = 42"""
    val closing = if (version.isScala3) "" else "\n}"
    val literal = s"""s"a$${
                       |$injection
                       |    }"""".stripMargin
    val text =
      s"""$opening
         |    val x = 1
         |
         |    val s = $literal
         |
         |$method
         |
         |    val tail = "end"$closing
         |""".stripMargin

    // SCL-25973: the literal must not swallow the following object members.
    val file = initFile(tempFileName, text)
    assertNull(PsiTreeUtil.findChildOfType(file, classOf[PsiErrorElement]))
    val string = PsiTreeUtil.findChildOfType(file, classOf[ScInterpolatedStringLiteral])
    assertNotNull(string)
    assertEquals(literal, string.getText)
    val obj = PsiTreeUtil.findChildOfType(file, classOf[ScObject])
    assertEquals(Seq("f"), obj.functions.map(_.name))
    assertEquals("val tail = \"end\"", obj.members.last.getText)

    doTextTest(text, actionRepeats = 2)
  }

  def testLineCommentInMultilineInjection(): Unit =
    checkCommentInInjection("        // comment\n        x")

  def testBlockCommentInMultilineInjection(): Unit =
    checkCommentInInjection("        /* comment */\n        x")

  def testTrailingCommentInMultilineInjection(): Unit =
    checkCommentInInjection("        x // comment")
}
