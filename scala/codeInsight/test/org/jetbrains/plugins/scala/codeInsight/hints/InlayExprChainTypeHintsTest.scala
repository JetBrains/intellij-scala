package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.util.Setter

class InlayExprChainTypeHintsTest extends InlayHintsTestBase {

  import Hint.{End => E, Start => S}
  import ScalaCodeInsightSettings.{getInstance => settings}

  def testChain(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .filter(_ > 2)
       |  .toSet$S: Set[Int]$E
       |  .toString
     """.stripMargin
  )

  def testChainInValDef(): Unit = doTest(
    s"""
       |val x = List(1, 2, 3)$S: List[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .filter(_ > 2)
       |  .toSet$S: Set[Int]$E
       |  .toString
     """.stripMargin
  )

  def testChainWithInfixCall(): Unit = doTest(
    s"""
       |val x = List(1, 2, 3)$S: List[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .filter(_ > 2)
       |  .toSet$S: Set[Int]$E
       |  .toString + ""
     """.stripMargin
  )

  def testChainInParenthesis_1(): Unit = doTest(
    s"""
       |(List(1, 2, 3)$S: List[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .filter(_ > 2)
       |  .toSet)$S: Set[Int]$E
       |  .toString
     """.stripMargin
  )

  def testChainInParenthesis_3(): Unit = doTest(
    s"""
       |(List(1, 2, 3)$S: List[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .filter(_ > 2)
       |  .toSet$S: Set[Int]$E
       |  .toString)
     """.stripMargin
  )

  def testChainInParenthesis_2(): Unit = doTest(
    s"""
       |(List(1, 2, 3)$S: List[Int]$E
       |  .map(_ + "")$S: List[String]$E
       |  .toSet)$S: Set[String]$E
       |  .toSet
     """.stripMargin
  )

  def testNoHintsWhenTurnedOf(): Unit = doTest(
    s"""
       |List(1, 2, 3)
       |  .toSeq
       |  .filter(_ > 2)
       |  .toSet
       |  .toString
     """.stripMargin,
    options = settings.showExpressionChainTypeSetter() -> false
  )

  def testBoringChainHasNoHints(): Unit = doTest(
    s"""
      |List(1, 2, 3)
      |  .filter(_ > 2)
      |  .filter(_ == 39)
      |  .map(_ + 3)
      |  .filter(_ < 2)
    """.stripMargin
  )

  def testChainWithoutLineBreaksHasNoHints(): Unit = doTest(
    s"""
       |List(1, 2, 3).toSeq.filter(_ > 2)
       |  .toSet.toString
     """.stripMargin
  )

  private def doTest(text: String, options: (Setter[java.lang.Boolean], Boolean)*): Unit = {
    def setOptions(reset: Boolean): Unit = options.foreach { case (opt, value) => opt.set(if(reset) !value else value) }

    try {
      setOptions(false)

      doInlayTest(text)
    } finally {
      setOptions(true)
    }
  }
}
