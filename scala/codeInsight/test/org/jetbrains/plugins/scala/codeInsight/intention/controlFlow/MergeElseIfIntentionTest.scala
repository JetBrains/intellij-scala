package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

class MergeElseIfIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.merge.else.if")

  def testMergeElseIf1(): Unit = {
    val text =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se {
         |      if (a == 8) {
         |        System.out.println("if2")
         |      } else {
         |        System.out.println("else")
         |      }
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se if (a == 8) {
         |      System.out.println("if2")
         |    } else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf2(): Unit = {
    val text =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else
         |        System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    el${CARET}se if (a == 8)
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf3(): Unit = {
    val text =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else {
         |        System.out.println("else")
         |      }
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se if (a == 8)
         |      System.out.println("if2")
         |    else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf4(): Unit = {
    val text =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else
         |        System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se if (a == 8)
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf5(): Unit = {
    val text =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9)
         |      System.out.println("if1")
         |    else${CARET} {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else
         |        System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    else${CARET} if (a == 8)
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf6(): Unit = {
    val text =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9)
         |      System.out.println("if1")
         |    else${CARET} {
         |      if (a == 8)
         |        System.out.println("if2")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class MergeElseIf {
         |  def mthd() {
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    else${CARET} if (a == 8)
         |      System.out.println("if2")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}

class MergeElseIfIntentionTest_Scala3 extends intentions.ScalaIntentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override def familyName = ScalaCodeInsightBundle.message("family.name.merge.else.if")

  def testMergeElseIf1(): Unit = {
    val text =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se {
         |      if (a == 8) {
         |        System.out.println("if2")
         |      } else {
         |        System.out.println("else")
         |      }
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if a == 9 then
         |      System.out.println("if1")
         |    el${CARET}se if a == 8 then
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf2(): Unit = {
    val text =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else
         |        System.out.println("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if a == 9 then System.out.println("if1")
         |    el${CARET}se if a == 8 then
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf3(): Unit = {
    val text =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else {
         |        System.out.println("else")
         |      }
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if a == 9 then
         |      System.out.println("if1")
         |    el${CARET}se if a == 8 then
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf4(): Unit = {
    val text =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else
         |        System.out.println("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if a == 9 then
         |      System.out.println("if1")
         |    el${CARET}se if a == 8 then
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf5(): Unit = {
    val text =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if (a == 9)
         |      System.out.println("if1")
         |    else$CARET {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else
         |        System.out.println("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if a == 9 then System.out.println("if1")
         |    else$CARET if a == 8 then
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeElseIf6(): Unit = {
    val text =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if (a == 9)
         |      System.out.println("if1")
         |    else$CARET {
         |      if (a == 8)
         |        System.out.println("if2")
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeElseIf:
         |  def mthd() =
         |    val a: Int = 0
         |    if a == 9 then System.out.println("if1")
         |    else$CARET if a == 8 then
         |      System.out.println("if2")
         |""".stripMargin

    doTest(text, resultText)
  }
}
