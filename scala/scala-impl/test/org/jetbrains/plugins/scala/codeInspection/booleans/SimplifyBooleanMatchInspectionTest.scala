package org.jetbrains.plugins.scala.codeInspection.booleans

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

abstract class SimplifyBooleanMatchInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[SimplifyBooleanMatchInspection]
  override protected val description = "Trivial match can be simplified"

  protected val hint = "Simplify match to if statement"
}

class SimplifyBooleanMatchInspectionTest extends SimplifyBooleanMatchInspectionTestBase {

  def test_SingleTrueWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${START}match$END {
         |  case true => 1
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_SingleTrueWithParenthesis_lessPatternSimpleBranchesBracesBlock(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${START}match$END {
         |  case true => {
         |    1
         |  }
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_SingleFalseWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${START}match$END {
         |  case false => 1
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_TrueFalseWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${START}match$END {
         |  case false => 1
         |  case true => 4
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_TrueWildcardWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${START}match$END {
         |  case true => 1
         |  case _ => 4
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_FalseWildcardWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${START}match$END {
         |  case false => 1
         |  case _ => 4
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_TrueFalseWithParenthesis_lessPatternBlockBranches(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |val b = a ${START}match$END {
         |  case true => {
         |    1
         |  }
         |  case false => {
         |    val t = 1
         |    t + 2
         |  }
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_SingleTrueWithParenthesisedPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""
         |val b = 1 + 2 == 3 ${START}match$END {
         |  case true => 1
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_SingleFalseWithParenthesisedPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""
         |val b = 1 + 2 == 3 ${START}match$END {
         |  case false => 1
         |}
       """.stripMargin
    checkTextHasError(selectedText)

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

    testQuickFix(text, result, hint)
  }

  def test_SingleComplexBranch(): Unit = {
    val text =
      s"""
         |val a = true
         |val b = a match {
         |  case a + 2 == 3 => {
         |    1
         |  }
         |}
       """.stripMargin

    checkTextHasNoErrors(text)
  }

  def test_ThreeBranches(): Unit = {
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

    checkTextHasNoErrors(text)
  }

  def test_NotBooleanExpr(): Unit = {
    val text =
      s"""
         |val a = 1 + 2
         |val b = a match {
         |  case a + 2 == 3 => {
         |    1
         |  }
         |}
       """.stripMargin

    checkTextHasNoErrors(text)
  }

  def test_OnlyWildcardExpr(): Unit = {
    val text =
      s"""
         |val a = true
         |val b = a match {
         |  case _ => {
         |    1
         |  }
         |}
       """.stripMargin

    checkTextHasNoErrors(text)
  }

  def test_TwoWildcatdsExpr(): Unit = {
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

    checkTextHasNoErrors(text)
  }

  def test_MatchWithGuard(): Unit = {
    val text =
      s"""
         |val a = true
         |val c = false
         |val b = a match {
         |  case true if c => 1
         |  case false => 4
         |}
       """.stripMargin

    checkTextHasNoErrors(text)
  }
}

class SimplifyBooleanMatchInspectionTest_Scala3 extends SimplifyBooleanMatchInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def trimExpectedText: Boolean = false

  //noinspection JUnitMalformedDeclaration
  private def testQuickFix(text: String, result: String): Unit =
    testQuickFix(text, result, hint)

  def test_SingleTrueWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""val a = true
         |val b = a ${START}match$END
         |  case true => 1
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val a = true
        |val b = a match
        | case true => 1
        |""".stripMargin
    val result =
      """val a = true
        |val b = if a then
        |  1""".stripMargin

    testQuickFix(text, result)
  }

  def test_SingleTrueWithParenthesis_lessPatternSimpleBranchesBracesBlock(): Unit = {
    val selectedText =
      s"""val a = true
         |val b = a ${START}match$END
         |  case true => {
         |    1
         |  }
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val a = true
        |val b = a match
        | case true => {
        |     1
        |   }
        |""".stripMargin
    val result =
      """val a = true
        |val b = if a then
        |  1""".stripMargin

    testQuickFix(text, result)
  }

  def test_SingleFalseWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""val a = true
         |val b = a ${START}match$END
         |  case false => 1
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val a = true
        |val b = a match
        | case false => 1
        |""".stripMargin
    val result =
      """val a = true
        |val b = if !a then
        |  1""".stripMargin

    testQuickFix(text, result)
  }

  def test_TrueFalseWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""val a = true
         |val b = a ${START}match$END
         |  case false => 1
         |  case true => 4
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val a = true
        |val b = a match
        | case false => 1
        | case true => 4
        |""".stripMargin
    val result =
      """val a = true
        |val b = if a then
        |  4
        |else
        |  1""".stripMargin

    testQuickFix(text, result)
  }

  def test_TrueWildcardWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""val a = true
         |val b = a ${START}match$END
         |  case true => 1
         |  case _ => 4
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val a = true
        |val b = a match
        | case true => 1
        | case _ => 4
        |""".stripMargin
    val result =
      """val a = true
        |val b = if a then
        |  1
        |else
        |  4""".stripMargin

    testQuickFix(text, result)
  }

  def test_FalseWildcardWithParenthesis_lessPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""val a = true
         |val b = a ${START}match$END
         |  case false => 1
         |  case _ => 4
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val a = true
        |val b = a match
        | case false => 1
        | case _ => 4
        |
        |val c = b - 1""".stripMargin
    val result =
      """val a = true
        |val b = if a then
        |  4
        |else
        |  1
        |
        |val c = b - 1""".stripMargin

    testQuickFix(text, result)
  }

  def test_TrueFalseWithParenthesis_lessPatternBlockBranches(): Unit = {
    val selectedText =
      s"""val a = true
         |val b = a ${START}match$END
         |  case true => {
         |    1
         |  }
         |  case false => {
         |    val t = 1
         |    t + 2
         |  }
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      s"""val a = true
         |val b = a match
         |  case true => {
         |    1
         |  }
         |  case false => {
         |    val t = 1
         |    t + 2
         |  }
         |""".stripMargin
    val result =
      """val a = true
        |val b = if a then
        |  1
        |else
        |  val t = 1
        |  t + 2""".stripMargin

    testQuickFix(text, result)
  }

  def test_SingleTrueWithParenthesisedPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""val b = 1 + 2 == 3 ${START}match$END
         |  case true => 1
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val b = 1 + 2 == 3 match
        | case true => 1
        |""".stripMargin
    val result =
      """val b = if 1 + 2 == 3 then
        |  1""".stripMargin

    testQuickFix(text, result)
  }

  def test_SingleFalseWithParenthesisedPatternSimpleBranches(): Unit = {
    val selectedText =
      s"""val b = 1 + 2 == 3 ${START}match$END
         |  case false => 1
         |""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val b = 1 + 2 == 3 match
        | case false => 1
        |""".stripMargin
    val result =
      """val b = if !(1 + 2 == 3) then
        |  1""".stripMargin

    testQuickFix(text, result)
  }

  def test_SingleComplexBranch(): Unit = {
    val text =
      s"""val a = true
         |val b = a match
         |  case a + 2 == 3 => {
         |    1
         |  }
         |""".stripMargin

    checkTextHasNoErrors(text)
  }

  def test_ThreeBranches(): Unit = {
    val text =
      s"""val a = 1
         |val b = a match
         |  case a + 2 == 3 => {
         |    1
         |  }
         |  case a + 2 == 4 => {
         |    2
         |  }
         |  case _ =>
         |""".stripMargin

    checkTextHasNoErrors(text)
  }

  def test_NotBooleanExpr(): Unit = {
    val text =
      s"""val a = 1 + 2
         |val b = a match
         |  case a + 2 == 3 => {
         |    1
         |  }
         |""".stripMargin

    checkTextHasNoErrors(text)
  }

  def test_OnlyWildcardExpr(): Unit = {
    val text =
      s"""val a = true
         |val b = a match
         |  case _ => {
         |    1
         |  }
         |""".stripMargin

    checkTextHasNoErrors(text)
  }

  def test_TwoWildcatdsExpr(): Unit = {
    val text =
      s"""val a = true
         |val b = a match
         |  case _ => {
         |    1
         |  }
         |  case _ => {
         |    2
         |  }
         |""".stripMargin

    checkTextHasNoErrors(text)
  }

  def test_MatchWithGuard(): Unit = {
    val text =
      s"""val a = true
         |val c = false
         |val b = a match
         |  case true if c => 1
         |  case false => 4
         |""".stripMargin

    checkTextHasNoErrors(text)
  }
}
