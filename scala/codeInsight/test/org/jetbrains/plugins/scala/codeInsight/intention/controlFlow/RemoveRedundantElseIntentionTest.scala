package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

class RemoveRedundantElseIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.remove.redundant.else")

  def testRemoveElse1(): Unit = {
    val text =
      s"""class X {
         |  def f(i: Int) {
         |    if (i == 0) {
         |      return
         |    } e${CARET}lse {
         |      val j = 0
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(i: Int) {
         |    if (i == 0) {
         |      return
         |    }$CARET
         |    val j = 0
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testRemoveElse2(): Unit = {
    val text =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0) {
         |      return true
         |    } e${CARET}lse {
         |      val j = 0
         |    }
         |    return false
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0) {
         |      return true
         |    }$CARET
         |    val j = 0
         |    return false
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testRemoveElse3(): Unit = {
    val text =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0) {
         |      System.out.println("if")
         |      return true
         |    } e${CARET}lse {
         |      System.out.println("else")
         |      val j = 0
         |    }
         |    return false
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0) {
         |      System.out.println("if")
         |      return true
         |    }$CARET
         |    System.out.println("else")
         |    val j = 0
         |    return false
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testRemoveElse4(): Unit = {
    val text =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0) return true
         |    e${CARET}lse {
         |      System.out.println("else")
         |      val j = 0
         |    }
         |    return false
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0) return true$CARET
         |    System.out.println("else")
         |    val j = 0
         |    return false
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testRemoveElse5(): Unit = {
    val text =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0)
         |      return true
         |    e${CARET}lse
         |      System.out.println("else")
         |    return false
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0)
         |      return true$CARET
         |    System.out.println("else")
         |    return false
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testRemoveElse6(): Unit = {
    val text =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0)
         |      throw new Exception
         |    e${CARET}lse
         |      System.out.println("else")
         |    return false
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(i: Int): Boolean = {
         |    if (i == 0)
         |      throw new Exception$CARET
         |    System.out.println("else")
         |    return false
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}