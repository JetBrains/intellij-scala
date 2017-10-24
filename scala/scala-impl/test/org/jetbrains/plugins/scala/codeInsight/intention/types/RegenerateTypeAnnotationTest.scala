package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class RegenerateTypeAnnotationTest extends ScalaIntentionTestBase {
  def familyName: String = RegenerateTypeAnnotation.FamilyName

  def testIntentionIsAvailable_Var() {
    checkIntentionIsAvailable("var x: Int = 1")
  }

  def testIntentionIsAvailable_Var_Incorrect() {
    checkIntentionIsAvailable("var x: Char = 1"
    )
  }

  def testIntentionIsAvailable_Val() {
    checkIntentionIsAvailable("val x: Int = 1")
  }

  def testIntentionIsAvailable_Val_Incorrect() {
    checkIntentionIsAvailable("val x: Char = 1"
    )
  }

  def testIntentionIsAvailable_Def() {
    checkIntentionIsAvailable("def f: Int = 1")
  }

  def testIntentionIsAvailable_Def_Incorrect() {
    checkIntentionIsAvailable("def f: Char = 1"
    )
  }

  def testIntentionIsNotAvailable_Var_WithoutType() {
    checkIntentionIsNotAvailable("var x = 1")
  }

  def testIntentionIsNotAvailable_Val_WithoutType() {
    checkIntentionIsNotAvailable("val x = 1")
  }

  def testIntentionIsNotAvailable_Def_WithoutType() {
    checkIntentionIsNotAvailable("def f = 1")
  }

  def testIntentionAction_Var() {
    val text = "var x: Char = 1"
    val resultText = "var x: Int = 1"

    doTest(text, resultText)
  }

  def testIntentionAction_Val() {
    val text = "val x: Char = 1"
    val resultText = "val x: Int = 1"

    doTest(text, resultText)
  }

  def testIntentionAction_Def() {
    val text = "def f: Char = 1"
    val resultText = "def f: Int = 1"

    doTest(text, resultText)
  }
}
