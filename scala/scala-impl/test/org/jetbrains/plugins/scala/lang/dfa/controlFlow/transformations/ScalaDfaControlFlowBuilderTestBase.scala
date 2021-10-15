package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.defaultCodeTemplate
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.junit.Assert.assertTrue

abstract class ScalaDfaControlFlowBuilderTestBase extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[ScalaDfaControlFlowBuilderTestBase])

  protected def codeFromMethodBody(returnType: String)(body: String): String = defaultCodeTemplate(returnType)(body)

  def test(code: String)(expectedResult: String): Unit = {
    val actualFile = configureFromFileText(code)
    var functionVisited = false

    actualFile.accept(new ScalaRecursiveElementVisitor {
      override def visitFunctionDefinition(function: ScFunctionDefinition): Unit = {
        for (body <- function.body if !functionVisited) {
          functionVisited = true

          val factory = new DfaValueFactory(getProject)
          val controlFlowBuilder = new ScalaDfaControlFlowBuilder(factory, body)
          new ScalaPsiElementTransformer(body).transform(controlFlowBuilder)
          val flow = controlFlowBuilder.build()

          flow.toString.trim.linesIterator.map(_.trim).mkString("\n") shouldBe expectedResult.trim
        }
      }
    })

    assertTrue("No function definition has been visited", functionVisited)
  }
}
