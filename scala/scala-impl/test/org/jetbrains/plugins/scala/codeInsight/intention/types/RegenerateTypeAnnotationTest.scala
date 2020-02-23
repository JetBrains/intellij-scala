package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class RegenerateTypeAnnotationTest extends ScalaIntentionTestBase {
  override def familyName: String = RegenerateTypeAnnotation.FamilyName

  def testIntentionIsAvailable_Var(): Unit = {
    checkIntentionIsAvailable("var x: Int = 1")
  }

  def testIntentionIsAvailable_Var_Incorrect(): Unit = {
    checkIntentionIsAvailable("var x: Char = 1"
    )
  }

  def testIntentionIsAvailable_Val(): Unit = {
    checkIntentionIsAvailable("val x: Int = 1")
  }

  def testIntentionIsAvailable_Val_Incorrect(): Unit = {
    checkIntentionIsAvailable("val x: Char = 1"
    )
  }

  def testIntentionIsAvailable_Def(): Unit = {
    checkIntentionIsAvailable("def f: Int = 1")
  }

  def testIntentionIsAvailable_Def_Incorrect(): Unit = {
    checkIntentionIsAvailable("def f: Char = 1"
    )
  }

  def testIntentionIsNotAvailable_Var_WithoutType(): Unit = {
    checkIntentionIsNotAvailable("var x = 1")
  }

  def testIntentionIsNotAvailable_Val_WithoutType(): Unit = {
    checkIntentionIsNotAvailable("val x = 1")
  }

  def testIntentionIsNotAvailable_Def_WithoutType(): Unit = {
    checkIntentionIsNotAvailable("def f = 1")
  }

  def testIntentionAction_Var(): Unit = {
    val text = "var x: Char = 1"
    val resultText = "var x: Int = 1"

    doTest(text, resultText)
  }

  def testIntentionAction_Val(): Unit = {
    val text = "val x: Char = 1"
    val resultText = "val x: Int = 1"

    doTest(text, resultText)
  }

  def testIntentionAction_Def(): Unit = {
    val text = "def f: Char = 1"
    val resultText = "def f: Int = 1"

    doTest(text, resultText)
  }
}
