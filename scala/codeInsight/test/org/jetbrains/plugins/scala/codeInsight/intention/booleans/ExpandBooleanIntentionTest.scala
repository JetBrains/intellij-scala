package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

class ExpandBooleanIntentionTest extends intentions.ScalaIntentionTestBase {

  override val familyName = ScalaCodeInsightBundle.message("family.name.expand.boolean")

  def testExpandBoolean1(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Int): Boolean = {
         |    retur${CARET}n a > 0
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Int): Boolean = {
         |    ${CARET}if (a > 0) {
         |      return true
         |    } else {
         |      return false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testExpandBoolean2(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Int): Boolean = {
         |    retur${CARET}n (a > 0)
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Int): Boolean = {
         |    ${CARET}if (a > 0) {
         |      return true
         |    } else {
         |      return false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testExpandBoolean3(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Int, b: Int): Boolean = {
         |    retur${CARET}n (a > 0 || b < 7)
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Int, b: Int): Boolean = {
         |    ${CARET}if (a > 0 || b < 7) {
         |      return true
         |    } else {
         |      return false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testExpandBoolean4(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Int, b: Int): Boolean = {
         |    if (a > 0 || b < 7) {
         |      ret${CARET}urn true
         |    } else {
         |      return false
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Int, b: Int): Boolean = {
         |    if (a > 0 || b < 7) {
         |      ${CARET}if (true) {
         |        return true
         |      } else {
         |        return false
         |      }
         |    } else {
         |      return false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

}

class ExpandBooleanIntentionTest_Scala3 extends intentions.ScalaIntentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override val familyName = ScalaCodeInsightBundle.message("family.name.expand.boolean")

  def testExpandBoolean1(): Unit = {
    val text =
      s"""
         |class X:
         |  def f(a: Int): Boolean =
         |    retur${CARET}n a > 0
         |""".stripMargin
    val resultText =
      s"""
         |class X:
         |  def f(a: Int): Boolean =
         |    ${CARET}if a > 0 then return true else return false
         |""".stripMargin

    doTest(text, resultText)
  }

  def testExpandBoolean2(): Unit = {
    val text =
      s"""
         |class X:
         |  def f(a: Int): Boolean =
         |    retur${CARET}n (a > 0)
         |""".stripMargin
    val resultText =
      s"""
         |class X:
         |  def f(a: Int): Boolean =
         |    ${CARET}if a > 0 then return true else return false
         |""".stripMargin

    doTest(text, resultText)
  }

  def testExpandBoolean3(): Unit = {
    val text =
      s"""
         |class X:
         |  def f(a: Int, b: Int): Boolean =
         |    retur${CARET}n (a > 0 || b < 7)
         |""".stripMargin
    val resultText =
      s"""
         |class X:
         |  def f(a: Int, b: Int): Boolean =
         |    ${CARET}if a > 0 || b < 7 then return true else return false
         |""".stripMargin

    doTest(text, resultText)
  }

  def testExpandBoolean4(): Unit = {
    val text =
      s"""
         |class X:
         |  def f(a: Int, b: Int): Boolean =
         |    if a > 0 || b < 7 then
         |      ret${CARET}urn true
         |    else
         |      return false
         |""".stripMargin
    val resultText =
      s"""
         |class X:
         |  def f(a: Int, b: Int): Boolean =
         |    if a > 0 || b < 7 then
         |      ${CARET}if true then return true else return false
         |    else
         |      return false
         |""".stripMargin

    doTest(text, resultText)
  }

}
