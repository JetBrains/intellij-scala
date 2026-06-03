package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.spellchecker.quickfixes.RenameTo

final class SpellCorrectionTest extends SpellCorrectionTestBase {
  def test_className(): Unit =
    doTest(s"class $NAME {}", "Testi")("Test")

  def test_objectName(): Unit =
    doTest(s"object $NAME {}", "Testi")("Test")

  def test_val(): Unit =
    doTest(s"object Obj { val $NAME = 0 }", "testi")("test")

  def test_def(): Unit =
    doTest(s"object Obj { def $NAME = 0 }", "testi")("test")

  def test_var(): Unit =
    doTest(s"object Obj { var $NAME = 0 }", "testi")("test")

  def test_meaningfulSingleSuggestionInRenameTo(): Unit = {
    val fileText =
      s"""
         |class A {
         |  // <TYPO descr="Typo: In word 'tagret'">tagret</TYPO>
         |  val <TYPO descr="Typo: In word 'tagret'">tag${CARET}ret</TYPO> = 1
         |}
         |""".stripMargin
    val resultText =
      """
        |class A {
        |  // target
        |  val target = 1
        |}
        |""".stripMargin

    myFixture.configureByText("A.scala", fileText)
    myFixture.enableInspections(classOf[GrazieSpellCheckingInspection])
    myFixture.checkHighlighting()
    val fix = myFixture.findSingleIntention(RenameTo.getFixName(java.util.List.of("target")))
    myFixture.launchAction(fix)
    myFixture.checkResult(resultText)
  }
}
