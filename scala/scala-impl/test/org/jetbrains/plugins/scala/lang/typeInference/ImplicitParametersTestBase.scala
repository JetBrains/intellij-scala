package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.junit.Assert

trait ImplicitParametersTestBase extends TypeInferenceTestBase {
  protected final def getImplicitArguments(fileText: String): Seq[ScalaResolveResult] = {
    val scalaFile: ScalaFile = configureFromFileText(
      "dummy.scala",
      Some(fileText.withNormalizedSeparator.trim)
    )

    val expr      = findSelectedExpression(scalaFile)
    val implicits = expr.findImplicitArguments

    Assert.assertTrue("Expression with implicit parameters expected", implicits.nonEmpty)

    val implicitArgsFlat = implicits.flatMap(_.args)
    implicitArgsFlat
  }

  def implicitArgumentsProblems(fileText: String): Seq[ScalaResolveResult] =
    getImplicitArguments(fileText).filter(_.isImplicitParameterProblem)

  def checkHasImplicitArgumentProblems(fileText: String): Unit =
    doTestImplicitArgs(fileText, expectedSomeProblems = true)

  def checkNoImplicitParameterProblems(fileText: String): Unit =
    doTestImplicitArgs(fileText, expectedSomeProblems = false)

  def doTestImplicitArgs(fileText: String, expectedSomeProblems: Boolean): Unit = {
    val implicits   = getImplicitArguments(fileText)
    val hasProblems = implicits.exists(_.isImplicitParameterProblem)

    if (expectedSomeProblems ^ shouldPass == hasProblems)
      Assert.fail("Problems in implicit parameters search: " + implicits.mkString("\n"))
  }
}
