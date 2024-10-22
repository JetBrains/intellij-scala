package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class MergeIfToOrIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.merge.equivalent.ifs.to.ored.condition")

  def testMergeIfToOr1(): Unit = {
    val text =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |    } else if (a == 7) {
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |    ${CARET}if (a == 9 || a == 7) {
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToOr2(): Unit = {
    val text =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |      System.out.println("if")
         |    } else if (a == 7) {
         |      System.out.println("if")
         |    } else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |    ${CARET}if (a == 9 || a == 7) {
         |      System.out.println("if")
         |    } else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToOr3(): Unit = {
    val text =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |      i${CARET}f (a == 9)
         |        System.out.println("if")
         |      else if (a == 7)
         |        System.out.println("if")
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |    ${CARET}if (a == 9 || a == 7) System.out.println("if")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToOr4(): Unit = {
    val text =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |      i${CARET}f (a == 9)
         |        System.out.println("if")
         |      else if (a == 7)
         |        System.out.println("if")
         |      else if (a == 19)
         |        System.out.println("if")
         |      else
         |        System.out.println("if")
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeIfToOr {
         |  def mthd {
         |    val a: Int = 0
         |    ${CARET}if (a == 9 || a == 7) System.out.println("if")
         |    else if (a == 19)
         |      System.out.println("if")
         |    else
         |      System.out.println("if")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class MergeIfToOrIntentionTest_Scala3 extends intentions.ScalaIntentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def familyName = ScalaCodeInsightBundle.message("family.name.merge.equivalent.ifs.to.ored.condition")

  def testMergeIfToOr1(): Unit = {
    val text =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |    } else if (a == 7) {
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    ${CARET}if a == 9 || a == 7 then {
         |    }
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToOr2(): Unit = {
    val text =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |      System.out.println("if")
         |    } else if (a == 7) {
         |      System.out.println("if")
         |    } else {
         |      System.out.println("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    ${CARET}if a == 9 || a == 7 then
         |      System.out.println("if")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToOr3(): Unit = {
    val text =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |      i${CARET}f (a == 9)
         |        System.out.println("if")
         |      else if (a == 7)
         |        System.out.println("if")
         |""".stripMargin
    val resultText =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    ${CARET}if a == 9 || a == 7 then System.out.println("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToOr4(): Unit = {
    val text =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |      i${CARET}f (a == 9)
         |        System.out.println("if")
         |      else if (a == 7)
         |        System.out.println("if")
         |      else if (a == 19)
         |        System.out.println("if")
         |      else
         |        System.out.println("if")
         |""".stripMargin
    val resultText =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    ${CARET}if a == 9 || a == 7 then System.out.println("if")
         |    else if a == 19 then
         |      System.out.println("if")
         |    else
         |      System.out.println("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToOr5(): Unit = {
    val text =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    ${CARET}if a == 9 || a == 7 then System.out.println("if")
         |    else if a == 19 then
         |      System.out.println("if")
         |    else
         |      System.out.println("if")
         |""".stripMargin
    val resultText =
      s"""class MergeIfToOr:
         |  def mthd =
         |    val a: Int = 0
         |    ${CARET}if a == 9 || a == 7 || a == 19 then System.out.println("if")
         |    else System.out.println("if")
         |""".stripMargin

    doTest(text, resultText)
  }
}
