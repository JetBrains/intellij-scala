package org.jetbrains.plugins.scala.annotator.quickfix

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword

abstract class PullUpQuickFixTest(keyword: String) extends ScalaAnnotatorQuickFixTestBase {
  protected val memberType: String

  def testSimplePullUp(): Unit = {
    val code =
      s"""
         |trait A
         |class B extends A {
         |   override $keyword ${START}sample$END: Int = 1
         |}
      """.stripMargin
    checkTextHasError(code)

    testQuickFix(
      code,
      s"""trait A {
         |
         |  $keyword sample: Int
         |}
         |
         |class B extends A {
         |  override $keyword ${START}sample$END: Int = 1
         |}
      """.stripMargin,
      s"Pull $memberType 'sample' to..."
    )
  }

  def testDoNotExtractIfParentIsNotEditable(): Unit = {
    val code =
      s"""
         |class A extends scala.App {
         |   override $keyword ${START}sample$END: Int = 1
         |}
      """.stripMargin
    checkTextHasError(code)
    checkIsNotAvailable(code, s"Pull $memberType 'sample' to...")
  }

  def testDoNotProposePullUpIfMemberExists(): Unit = {
    val code =
      s"""
         |trait A {
         |  $keyword sample: Int
         |}
         |class B extends A {
         |   override $keyword ${START}sample$END: Int = 1
         |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

}

class MethodPullUpQuickFix extends PullUpQuickFixTest(ScalaKeyword.DEF) {
  override protected val description = "Method 'sample' overrides nothing"
  override protected val memberType = "method"
}

class ValPullUpQuickFix extends PullUpQuickFixTest(ScalaKeyword.VAL) {
  override protected val description = "Value 'sample' overrides nothing"
  override protected val memberType = "value"
}

class VarPullUpQuickFix extends PullUpQuickFixTest(ScalaKeyword.VAR) {
  override protected val description = "Variable 'sample' overrides nothing"
  override protected val memberType = "variable"
}
