package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Ksenia.Sautina
  * @since 6/29/12
  */
class ExpandBooleanIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

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