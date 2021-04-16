package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.junit.Assert

trait ImplicitParametersTestBase extends TypeInferenceTestBase {

  def checkNoImplicitParameterProblems(fileText: String): Unit = {
    val scalaFile: ScalaFile = configureFromFileText(
      "dummy.scala",
      Some(ScalaLightCodeInsightFixtureTestAdapter.normalize(fileText))
    )

    val expr: ScExpression = findExpression(scalaFile)

    expr.findImplicitArguments match {
      case None =>
        Assert.fail("Expression with implicit parameters expected")
      case Some(seq) =>
        val hasProblems = seq.exists(_.isImplicitParameterProblem)
        if (shouldPass == hasProblems)
          Assert.fail("Problems in implicit parameters search: " + seq.mkString("\n"))
    }
  }
}
