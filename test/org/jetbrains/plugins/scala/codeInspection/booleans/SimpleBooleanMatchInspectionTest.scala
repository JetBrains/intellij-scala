package org.jetbrains.plugins.scala.codeInspection.booleans

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class SimpleBooleanMatchInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val s = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val e = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END
  val annotation = "Simplify trivial match to if"

  private def check(text: String) {
    checkTextHasError(text, annotation, classOf[SimpleBooleanMatchInspection])
  }

  private def testFix(text: String, result: String, hint: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, classOf[SimpleBooleanMatchInspection])
  }

  private def checkHasNoErrors(text: String) {
    checkTextHasNoErrors(text, annotation, classOf[SimpleBooleanMatchInspection])
  }

  def test_SingleTrueWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = ${s}a match {
         |  case true => 1
         |}$e
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val a = true
        |val b = a match {
        | case true => 1
        | }
      """.stripMargin
    val result =
      """
        |val a = true
        |val b = if (a) {
        |  1
        |}
      """.stripMargin

    testFix(text, result, "Simplify trivial match to if")
  }

  def test_SingleTrueWithParentlessPatternSimpleBranchesBracesBlock() {
    val selectedText =
      s"""
         |val a = true
         |val b = ${s}a match {
         |  case true => {
         |    1
         |  }
         |}$e
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val a = true
        |val b = a match {
        | case true => {
        |     1
        |   }
        | }
      """.stripMargin
    val result =
      """
        |val a = true
        |val b = if (a) {
        |  1
        |}
      """.stripMargin

    testFix(text, result, "Simplify trivial match to if")
  }

  def test_SingleFalseWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = ${s}a match {
         |  case false => 1
         |}$e
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val a = true
        |val b = a match {
        | case false => 1
        | }
      """.stripMargin
    val result =
      """
        |val a = true
        |val b = if (!a) {
        |  1
        |}
      """.stripMargin

    testFix(text, result, "Simplify trivial match to if")
  }

  def test_TrueFalseWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = ${s}a match {
         |  case false => 1
         |  case true => 4
         |}$e
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val a = true
        |val b = a match {
        | case false => 1
        | case true => 4
        | }
      """.stripMargin
    val result =
      """
        |val a = true
        |val b = if (a) {
        |  4
        |} else {
        |  1
        |}
      """.stripMargin

    testFix(text, result, "Simplify trivial match to if")
  }

  def test_TrueFalseWithParentlessPatternBlockBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = ${s}a match {
         |  case true => {
         |    1
         |  }
         |  case false => {
         |    val t = 1
         |    t + 2
         |  }
         |}$e
       """.stripMargin
    check(selectedText)

    val text =
      s"""
         |val a = true
         |val b = a match {
         |  case true => {
         |    1
         |  }
         |  case false => {
         |    val t = 1
         |    t + 2
         |  }
         |}
       """.stripMargin
    val result =
      """
        |val a = true
        |val b = if (a) {
        |  1
        |} else {
        |  val t = 1
        |  t + 2
        |}
      """.stripMargin

    testFix(text, result, "Simplify trivial match to if")
  }

  def test_SingleTrueWithParenthesisedPatternSimpleBranches() {
    val selectedText =
      s"""
         |val b = ${s}1 + 2 == 3 match {
         |  case true => 1
         |}$e
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val b = 1 + 2 == 3 match {
        | case true => 1
        | }
      """.stripMargin
    val result =
      """
        |val b = if (1 + 2 == 3) {
        |  1
        |}
      """.stripMargin

    testFix(text, result, "Simplify trivial match to if")
  }

  def test_SingleFalseWithParenthesisedPatternSimpleBranches() {
    val selectedText =
      s"""
         |val b = ${s}1 + 2 == 3 match {
         |  case false => 1
         |}$e
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val b = 1 + 2 == 3 match {
        | case false => 1
        | }
      """.stripMargin
    val result =
      """
        |val b = if (!(1 + 2 == 3)) {
        |  1
        |}
      """.stripMargin

    testFix(text, result, "Simplify trivial match to if")
  }

  def test_SingleComplexBranch() {
    val text =
      s"""
         |val a = true
         |val b = a match {
         |  case a + 2 == 3 {
         |    1
         |  }
         |}
       """.stripMargin

    checkHasNoErrors(text)
  }

  def test_ThreeBranches() {
    val text =
      s"""
         |val a = 1
         |val b = a match {
         |  case a + 2 == 3 {
         |    1
         |  }
         |  case a + 2 == 4 {
         |    2
         |  }
         |  case _ =>
         |}
       """.stripMargin

    checkHasNoErrors(text)
  }

  def test_NotBooleanExpr() {
    val text =
      s"""
         |val a = 1 + 2
         |val b = a match {
         |  case a + 2 == 3 {
         |    1
         |  }
         |}
       """.stripMargin

    checkHasNoErrors(text)
  }
}
