package org.jetbrains.plugins.scala
package lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.commonCodeTemplate
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ScalaPsiElementTransformer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
abstract class ScalaDfaControlFlowBuilderTestBase extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[ScalaDfaControlFlowBuilderTestBase])

  protected def codeFromMethodBody(returnType: String)(body: String): String = commonCodeTemplate(returnType)(body)

  def test(code: String)(expectedResult: String): Unit = {
    val actualFile = configureFromFileText(code)
    var functionVisited = false

    actualFile.accept(new ScalaRecursiveElementVisitor {
      override def visitFunctionDefinition(function: ScFunctionDefinition): Unit = {
        for (body <- function.body if !functionVisited) {
          functionVisited = true

          val factory = new DfaValueFactory(getProject)
          val analysedMethodInfo = AnalysedMethodInfo(function, 1)
          val controlFlowBuilder = new ScalaDfaControlFlowBuilder(analysedMethodInfo, factory, body)
          new ScalaPsiElementTransformer(body).transform(controlFlowBuilder)
          val flow = controlFlowBuilder.build()

          flow.toString.trim.linesIterator.map(_.trim).mkString("\n") shouldBe expectedResult.trim.withNormalizedSeparator
        }
      }
    })

    assertTrue("No function definition has been visited", functionVisited)
  }
}
