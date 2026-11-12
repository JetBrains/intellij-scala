package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, SimpleTestCase}
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests function-type flags and parameter/result accessors in the presence of captures.
 * Covers ordinary, pure and context arrows, including a pure callback nested in a dependent type.
 * These checks concern syntax-backed accessors, without computing function types.
 */
class ScFunctionalTypeElementTest extends SimpleTestCase {
  override protected def scalaCodeParsingFeatures: ScalaFeatures = CaptureCheckingTestUtils.parsingFeatures

  def testPureFunctionFlagWithCaptureFilters(): Unit = {
    assertPureFunctionResult(
      function = functionType("() ->{this.only[UserTable]} List[User]"),
      expectedResultTypeText = "List[User]"
    )
    assertPureFunctionResult(
      function = functionType("() ->{this.except[TransactionTable]} List[User]"),
      expectedResultTypeText = "List[User]"
    )

    val ordinaryFunction = functionType("() => Unit")
    val isPure = ordinaryFunction.isPure
    assertFalse(
      s"ordinary function type `${ordinaryFunction.getText}`: expected isPure=false, actual=$isPure",
      isPure
    )
  }

  def testContextFunctionFlagWithCaptureFilters(): Unit = {
    assertTrue(functionType("Scope ?->{any.only[Shared]} A").isContext)
    assertTrue(functionType("this.Raise ?->{any.except[ErrHandler]} A").isContext)
    assertFalse(functionType("() ->{any.only[C]} A").isContext)
  }

  def testContextFunctionWithExceptFilterParameterAndResultExcludeCaptureSet(): Unit = {
    val function = functionType("this.Raise ?->{any.except[ErrHandler]} A")
    assertFunctionParameterText(
      function = function,
      expectedParameterTypeText = "this.Raise"
    )
    assertPureFunctionResult(
      function = function,
      expectedResultTypeText = "A"
    )
  }

  def testContextFunctionWithOnlyFilterParameterAndResultExcludeCaptureSet(): Unit = {
    val function = functionType("Scope ?->{any.only[Shared]} A")
    assertFunctionParameterText(
      function = function,
      expectedParameterTypeText = "Scope"
    )
    assertPureFunctionResult(
      function = function,
      expectedResultTypeText = "A"
    )
  }

  def testPureCallbackResultExcludesCaptureSet(): Unit = {
    assertPureFunctionResult(
      function = functionType("() ->{C.only[Control]} T"),
      expectedResultTypeText = "T"
    )
    assertPureFunctionResult(
      function = functionType("() ->{C.except[Control]} T"),
      expectedResultTypeText = "T"
    )
  }

  def testNestedCallbackResultInDependentFunction(): Unit = {
    val outer =
      "type F = (tx: Transaction^) -> (() ->{E, tx.except[Commit].except[Rollback]} T)".parse[ScDependentFunctionTypeElement]
    assertEquals("(tx: Transaction^)", outer.parameterClause.getText)

    val result = outer.returnTypeElement.get.asInstanceOf[ScParenthesisedTypeElement]
    val inner = result.innerElement.get.asInstanceOf[ScFunctionalTypeElement]
    assertFunctionParameterText(
      function = inner,
      expectedParameterTypeText = "()"
    )
    assertPureFunctionResult(
      function = inner,
      expectedResultTypeText = "T"
    )
  }

  /** Checks purity and result text while reporting the complete function-type source. */
  private def assertPureFunctionResult(
    function: ScFunctionalTypeElement,
    expectedResultTypeText: String
  ): Unit = {
    val context = s"function type `${function.getText}`"
    val isPure = function.isPure
    assertTrue(s"$context: expected isPure=true, actual=$isPure", isPure)

    val actualResultTypeText = function.returnTypeElement.map(_.getText)
    assertEquals(
      s"$context: result type must be present and exclude the capture set",
      Some(expectedResultTypeText),
      actualResultTypeText
    )
  }

  /** Checks parameter text and reports a missing parameter before accessing its text. */
  private def assertFunctionParameterText(
    function: ScFunctionalTypeElement,
    expectedParameterTypeText: String
  ): Unit = {
    val context = s"function type `${function.getText}`"
    val parameterType = function.paramTypeElement
    assertNotNull(s"$context: expected parameter type `$expectedParameterTypeText`", parameterType)

    val actualParameterTypeText = parameterType.getText
    assertEquals(
      s"$context: parameter type must exclude the capture set",
      expectedParameterTypeText,
      actualParameterTypeText
    )
  }

  private def functionType(source: String): ScFunctionalTypeElement = s"type F = $source".parse[ScFunctionalTypeElement]
}
