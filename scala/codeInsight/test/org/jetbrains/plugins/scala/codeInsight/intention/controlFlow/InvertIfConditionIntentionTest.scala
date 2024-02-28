package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

class InvertIfConditionIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.invert.if.condition")

  def testInvertIf1(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    ${CARET}if (a) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    ${CARET}if (!a) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf2(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a) {
         |      b = false
         |    }
         |    System.out.println()
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (!a) {
         |
         |    } else {
         |      b = false
         |    }
         |    System.out.println()
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf3(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a == b) {
         |      val c = false
         |    }
         |    println()
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a != b) {
         |
         |    } else {
         |      val c = false
         |    }
         |    println()
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf4(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (!a) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf5(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf6(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (!(a == true)) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a == true) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf7(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if$CARET (false) {
         |
         |    } else {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if$CARET (true) {
         |      System.out.print("else")
         |    } else {
         |
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf8(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) {
         |      System.out.print("if")
         |    } else {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else")
         |    } else {
         |      System.out.print("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
  def testInvertIf_CaretOnCondition(): Unit = {
    val text =
      s"""
         |class X {
         |  def bar(i: Int): Unit = {
         |    if (i > 1${CARET}23) {
         |      println("1")
         |    } else {
         |      println("2")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def bar(i: Int): Unit = {
         |    if (i <= ${CARET}123) {
         |      println("2")
         |    } else {
         |      println("1")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnCondition_MultipleBranches(): Unit = {
    val text =
      s"""
         |class X {
         |  def bar(i: Int): Unit = {
         |    if (i >$CARET 123) {
         |      println("1")
         |    } else if (i < 123) {
         |      println("2")
         |    } else {
         |      println("3")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def bar(i: Int): Unit = {
         |    if (i <$CARET= 123) {
         |      if (i < 123) {
         |        println("2")
         |      } else {
         |        println("3")
         |      }
         |    } else {
         |      println("1")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnSecondBranchCondition_MultipleBranches(): Unit = {
    val text =
      s"""
         |class X {
         |  def bar(i: Int): Unit = {
         |    if (i > 123) {
         |      println("1")
         |    } else if (i$CARET < 123) {
         |      println("2")
         |    } else {
         |      println("3")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def bar(i: Int): Unit = {
         |    if (i > 123) {
         |      println("1")
         |    } else if (i$CARET >= 123) {
         |      println("3")
         |    } else {
         |      println("2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoBraces(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false)
         |      System.out.print("if")
         |    else
         |      System.out.print("else")
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else")
         |    } else {
         |      System.out.print("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoBraces_SameLine(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) System.out.print("if")
         |    else System.out.print("else")
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else")
         |    } else {
         |      System.out.print("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoIfBraces(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) System.out.print("if")
         |    else {
         |      System.out.print("else1")
         |      System.out.print("else2")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else1")
         |      System.out.print("else2")
         |    } else {
         |      System.out.print("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoElseBraces(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |    else
         |      System.out.print("else")
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else")
         |    } else {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretAtElse(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    } ${CARET}else {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (true) {
         |      System.out.print("else")
         |    }$CARET else {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretInsideElse(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    } el${CARET}se {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (true) {
         |      System.out.print("else")
         |    }$CARET else {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}

class InvertIfConditionIntentionTest_Scala3 extends intentions.ScalaIntentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override def familyName = ScalaCodeInsightBundle.message("family.name.invert.if.condition")

  def testInvertIf1(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    ${CARET}if (a) b = false
         |end X""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    ${CARET}if !a then {
         |
         |    } else
         |      b = false
         |end X""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf2(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (a) {
         |      b = false
         |    }
         |    System.out.println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f !a then {
         |
         |    } else
         |      b = false
         |    System.out.println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf3(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (a == b) {
         |      val c = false
         |    }
         |    println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f a != b then {
         |
         |    } else
         |      val c = false
         |    println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf4(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (!a) b = false
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f a then {
         |
         |    } else
         |      b = false
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf5(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (true) b = false
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f false then {
         |
         |    } else
         |      b = false
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf6(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (!(a == true)) b = false
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f a == true then {
         |
         |    } else
         |      b = false
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf7(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if$CARET (false) {
         |
         |    } else {
         |      System.out.print("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if$CARET true then
         |      System.out.print("else")
         |    else {
         |
         |    }
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf8(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (false) {
         |      System.out.print("if")
         |    } else {
         |      System.out.print("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f true then
         |      System.out.print("else")
         |    else
         |      System.out.print("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnCondition(): Unit = {
    val text =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > ${CARET}123 then
         |      println("1")
         |    else
         |      println("2")
         |    println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def bar(i: Int) =
         |    if i <=$CARET 123 then
         |      println("2")
         |    else
         |      println("1")
         |    println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnThen(): Unit = {
    val text =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > 123 th${CARET}en
         |      println("1")
         |    else
         |      println("2")
         |    println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def bar(i: Int) =
         |    if i <= 123 t${CARET}hen
         |      println("2")
         |    else
         |      println("1")
         |    println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnCondition_MultipleBranches(): Unit = {
    val text =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > ${CARET}123 then
         |      println("1")
         |    else if i < 123 then
         |      println("2")
         |    else
         |      println("3")
         |    println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def bar(i: Int) =
         |    if i <=$CARET 123 then
         |      if i < 123 then
         |        println("2")
         |      else
         |        println("3")
         |    else
         |      println("1")
         |    println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnThen_MultipleBranches(): Unit = {
    val text =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > 123 th${CARET}en
         |      println("1")
         |    else if i < 123 then
         |      println("2")
         |    else
         |      println("3")
         |    println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def bar(i: Int) =
         |    if i <= 123 t${CARET}hen
         |      if i < 123 then
         |        println("2")
         |      else
         |        println("3")
         |    else
         |      println("1")
         |    println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnSecondBranchCondition_MultipleBranches(): Unit = {
    val text =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > 123 then
         |      println("1")
         |    else if i$CARET < 123 then
         |      println("2")
         |    else
         |      println("3")
         |    println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > 123 then
         |      println("1")
         |    else if i$CARET >= 123 then
         |      println("3")
         |    else
         |      println("2")
         |    println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretOnSecondBranchThen_MultipleBranches(): Unit = {
    val text =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > 123 then
         |      println("1")
         |    else if i < 123 the${CARET}n
         |      println("2")
         |    else
         |      println("3")
         |    println()
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def bar(i: Int) =
         |    if i > 123 then
         |      println("1")
         |    else if i >= 123 th${CARET}en
         |      println("3")
         |    else
         |      println("2")
         |    println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoBraces(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (false)
         |      System.out.print("if")
         |    else
         |      System.out.print("else")
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f true then
         |      System.out.print("else")
         |    else
         |      System.out.print("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoBraces_SameLine(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (false) System.out.print("if")
         |    else System.out.print("else")
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f true then
         |      System.out.print("else")
         |    else
         |      System.out.print("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoIfBraces(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (false) System.out.print("if")
         |    else {
         |      System.out.print("else1")
         |      System.out.print("else2")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f true then
         |      System.out.print("else1")
         |      System.out.print("else2")
         |    else
         |      System.out.print("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoElseBraces(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |    else
         |      System.out.print("else")
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    i${CARET}f true then
         |      System.out.print("else")
         |    else
         |      System.out.print("if1")
         |      System.out.print("if2")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretAtElse(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    } ${CARET}else {
         |      System.out.print("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if true then
         |      System.out.print("else")$CARET
         |    else
         |      System.out.print("if1")
         |      System.out.print("if2")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretInsideElse(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    } el${CARET}se {
         |      System.out.print("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if true then
         |      System.out.print("else")$CARET
         |    else
         |      System.out.print("if1")
         |      System.out.print("if2")
         |""".stripMargin

    doTest(text, resultText)
  }
}
