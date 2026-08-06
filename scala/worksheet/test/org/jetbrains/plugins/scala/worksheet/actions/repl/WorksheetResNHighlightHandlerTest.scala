package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.jdk.CollectionConverters._

class WorksheetResNHighlightHandlerTest extends ScalaLightCodeInsightFixtureTestCase {

  private def doTest(
    fileText: String,
    expectedReadUsages: Seq[String],
    expectedWriteUsages: Seq[String],
  ): Unit = {
    myFixture.configureByText(s"${this.getTestName(false)}.sc", fileText)

    val handler = createHandler
    val targets = handler.getTargets
    handler.computeUsages(targets)

    val readUsages = handler.getReadUsages
    val writeUsages = handler.getWriteUsages

    val document = myFixture.getEditor.getDocument

    def textWithLineNumber(range: TextRange): String = {
      val lineNumber = document.getLineNumber(range.getStartOffset)
      s"$lineNumber:${range.substring(getFile.getText)}"
    }

    val readUsagesText = readUsages.asScala.map(textWithLineNumber).toSeq
    val writeUsagesText = writeUsages.asScala.map(textWithLineNumber).toSeq

    assertCollectionEquals("Read highlighted usages do not match", expectedReadUsages, readUsagesText)
    assertCollectionEquals("Write highlighted usages do not match", expectedWriteUsages, writeUsagesText)
  }

  private def createHandler: HighlightUsagesHandlerBase[PsiElement] = {
    HighlightUsagesHandler.createCustomHandler(getEditor, getFile).asInstanceOf[HighlightUsagesHandlerBase[PsiElement]]
  }

  def testNoHighlighting0(): Unit = {
    val code =
      s"""res0$CARET
         |""".stripMargin
    doTest(code, Seq(), Seq())
  }

  def testNoHighlighting1(): Unit = {
    val code =
      s"""res3$CARET
         |""".stripMargin
    doTest(code, Seq(), Seq())
  }

  def testNoHighlighting2(): Unit = {
    val code =
      s"""res0$CARET
         |000
         |111
         |222
         |""".stripMargin
    doTest(code, Seq(), Seq())
  }


  def testNoHighlighting3(): Unit = {
    val code =
      s"""000
         |res1$CARET
         |111
         |222
         |""".stripMargin
    doTest(code, Seq(), Seq())
  }

  def testHighlighting1(): Unit = {
    val code =
      s"""000
         |111
         |res1$CARET
         |222
         |""".stripMargin
    doTest(code, Seq("2:res1"), Seq("1:111"))
  }

  def testHighlighting2(): Unit = {
    val code =
      s"""000
         |111
         |222
         |res1$CARET
         |""".stripMargin
    doTest(code, Seq("3:res1"), Seq("1:111"))
  }


  def testHighlighting3(): Unit = {
    val code =
      s"""000
         |111
         |222
         |res2$CARET
         |""".stripMargin
    doTest(code, Seq("3:res2"), Seq("2:222"))
  }

  def test1(): Unit = {
    val code =
      s"""val res1 = 1
         |val res2 = 2
         |val res3 = 3
         |
         |res3 //REPL implicitly defines res0
         |
         |res2 //REPL implicitly (re)defines res1
         |
         |val res1 = 1
         |
         |res1$CARET
         |""".stripMargin
    doTest(
      code,
      Seq("10:res1"),
      Seq("0:res1", "6:res2", "8:res1")
    )
  }
}