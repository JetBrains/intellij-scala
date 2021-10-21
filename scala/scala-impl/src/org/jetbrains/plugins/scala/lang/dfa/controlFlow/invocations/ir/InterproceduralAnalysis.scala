package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.interpreter.{RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.scala.lang.dfa.analysis.DummyDfaListener
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ScalaPsiElementTransformer
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

object InterproceduralAnalysis {

  // TODO add evaluated arguments to the stack
  // TODO add limitations on depth, size, recursion etc. + add a flag to not report anything in an nested call
  def tryInterpretExternalMethod(invocationInfo: InvocationInfo, stateBefore: DfaMemoryState,
                                 factory: DfaValueFactory): Option[DfType] = {
    invocationInfo.invokedElement match {
      case Some(InvokedElement(function: ScFunctionDefinition))
        if supportsInterproceduralAnalysis(function) => function.body match {
        case Some(body) => analyseExternalMethodBody(body, stateBefore, factory)
        case _ => None
      }
      case _ => None
    }
  }

  private def supportsInterproceduralAnalysis(function: ScFunctionDefinition): Boolean = {
    function.hasModifierPropertyScala(PsiModifier.FINAL) || function.hasModifierPropertyScala(PsiModifier.PRIVATE)
  }

  private def analyseExternalMethodBody(body: ScExpression, stateBefore: DfaMemoryState,
                                        factory: DfaValueFactory): Option[DfType] = {
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(factory, body)
    new ScalaPsiElementTransformer(body).transform(controlFlowBuilder)

    val resultDestination = factory.getVarFactory.createVariableValue(MethodResultDescriptor())
    val flow = controlFlowBuilder.buildAndReturn(resultDestination)

    val listener = new DummyDfaListener
    val interpreter = new StandardDataFlowInterpreter(flow, listener)

    if (interpreter.interpret(stateBefore) != RunnerResult.OK) None
    else Some(stateBefore.getDfType(resultDestination))
  }
}
