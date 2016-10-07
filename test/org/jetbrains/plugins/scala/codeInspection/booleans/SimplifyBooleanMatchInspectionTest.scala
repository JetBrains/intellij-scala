package org.jetbrains.plugins.scala.codeInspection.booleans

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class SimplifyBooleanMatchInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val s = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val e = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END
  val annotation = "Trivial match can be simplified"
  val quickFixHint = "Simplify match to if statement"

  private def check(text: String) {
    checkTextHasError(text, annotation, classOf[SimplifyBooleanMatchInspection])
  }

  private def testFix(text: String, result: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), quickFixHint, classOf[SimplifyBooleanMatchInspection])
  }

  private def checkHasNoErrors(text: String) {
    checkTextHasNoErrors(text, annotation, classOf[SimplifyBooleanMatchInspection])
  }

  def test_SingleTrueWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${s}match$e {
         |  case true => 1
         |}
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

    testFix(text, result)
  }

  def test_SingleTrueWithParentlessPatternSimpleBranchesBracesBlock() {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${s}match$e {
         |  case true => {
         |    1
         |  }
         |}
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

    testFix(text, result)
  }

  def test_SingleFalseWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${s}match$e {
         |  case false => 1
         |}
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

    testFix(text, result)
  }

  def test_TrueFalseWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${s}match$e {
         |  case false => 1
         |  case true => 4
         |}
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

    testFix(text, result)
  }

  def test_TrueWildcardWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${s}match$e {
         |  case true => 1
         |  case _ => 4
         |}
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val a = true
        |val b = a match {
        | case true => 1
        | case _ => 4
        | }
      """.stripMargin
    val result =
      """
        |val a = true
        |val b = if (a) {
        |  1
        |} else {
        |  4
        |}
      """.stripMargin

    testFix(text, result)
  }

  def test_FalseWildcardWithParentlessPatternSimpleBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${s}match$e {
         |  case false => 1
         |  case _ => 4
         |}
       """.stripMargin
    check(selectedText)

    val text =
      """
        |val a = true
        |val b = a match {
        | case false => 1
        | case _ => 4
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

    testFix(text, result)
  }

  def test_TrueFalseWithParentlessPatternBlockBranches() {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${s}match$e {
         |  case true => {
         |    1
         |  }
         |  case false => {
         |    val t = 1
         |    t + 2
         |  }
         |}
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

    testFix(text, result)
  }

  def test_SingleTrueWithParenthesisedPatternSimpleBranches() {
    val selectedText =
      s"""
         |val b = 1 + 2 == 3 ${s}match$e {
         |  case true => 1
         |}
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

    testFix(text, result)
  }

  def test_SingleFalseWithParenthesisedPatternSimpleBranches() {
    val selectedText =
      s"""
         |val b = 1 + 2 == 3 ${s}match$e {
         |  case false => 1
         |}
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

    testFix(text, result)
  }

  def test_SingleComplexBranch() {
    val text =
      s"""
         |val a = true
         |val b = a match {
         |  case a + 2 == 3 => {
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
         |  case a + 2 == 3 => {
         |    1
         |  }
         |  case a + 2 == 4 => {
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
         |  case a + 2 == 3 => {
         |    1
         |  }
         |}
       """.stripMargin

    checkHasNoErrors(text)
  }

  def test_OnlyWildcardExpr() {
    val text =
      s"""
         |val a = true
         |val b = a match {
         |  case _ => {
         |    1
         |  }
         |}
       """.stripMargin

    checkHasNoErrors(text)
  }

  def test_TwoWildcatdsExpr() {
    val text =
      s"""
         |val a = true
         |val b = a match {
         |  case _ => {
         |    1
         |  }
         |  case _ => {
         |    2
         |  }
         |}
       """.stripMargin

    checkHasNoErrors(text)
  }

  def test_MatchWithGuard() {
    val text =
      s"""
         |val a = true
         |val c = false
         |val b = a match {
         |  case true if c => 1
         |  case false => 4
         |}
       """.stripMargin

    checkHasNoErrors(text)
  }
}
