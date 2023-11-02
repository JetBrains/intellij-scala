package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaValueFactory, DfaVariableValue}
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaDfaAnchor
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder.StackValue
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform._
import org.jetbrains.plugins.scala.lang.dfa.types.DfUnitType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

final class ScalaDfaControlFlowBuilder(val analysedMethodInfo: AnalysedMethodInfo,
                                       factory: DfaValueFactory,
                                       context: ScalaPsiElement,
                                       val buildUnsupportedPsiElements: Boolean = true)
  extends InstructionBuilder(factory, context)
    with DefinitionTransformation
    with ExpressionTransformation
    with StatementTransformation
    with InvocationTransformation
    with PatternMatchTransformation
    with TransformerUtils
    with specialSupport.CollectionAccessAssertionUtils
    with specialSupport.SpecialSyntheticMethodsTransformation
{
  private[controlFlow] implicit val rreqBuilderContext: ResultReq.BuilderContext = new ResultReq.BuilderContext(this)

  /**
   * Version of [[build]] to be used for building control flow of external methods in
   * interprocedural analysis. Instead of popping the return value, it assigns it
   * to the place specified in the parameter. It also takes into account possible throw/return instructions,
   * if they can appear somewhere inside the method's body.
   *
   * @param returnDestination DFA value to which the result of the transformed method
   *                          will be assigned after it is executed.
   * @param endOffset         deferred offset that will point to the instruction directly before the last one,
   *                          it can be used as a place to redirect possible exception/return statements
   * @return [[com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow]] representation
   *         of instructions that have been pushed to this builder's stack.
   */
  def buildForExternalMethod(returnDestination: DfaVariableValue, endOffset: DeferredOffset): ControlFlow = {
    ???
    /*val finishOffset = new DeferredOffset
    addInstruction(new SimpleAssignmentInstruction(null, returnDestination))
    goto(finishOffset)

    anchorLabel(endOffset)
    pushUnknownValue()
    addInstruction(new SimpleAssignmentInstruction(null, returnDestination))

    anchorLabel(finishOffset)
    flow.finish()
    flow*/
  }

  def unsupported[R](exception: => Exception)(fallback: => R): R = {
    if (buildUnsupportedPsiElements) {
      fallback
    } else {
      throw exception
    }
  }

  def unsupported[R](element: ScalaPsiElement)(fallback: => R): R = {
    unsupported(TransformationFailedException(element, "Currently unsupported element"))(fallback)
  }

  def pushUnit(): StackValue = push(DfUnitType)
  def pushUnit(rreq: ResultReq = ResultReq.Required): rreq.Result =
    rreq.ifResultNeeded {
      pushUnit()
    }

  def pushUnknownValue(): StackValue = push(DfType.TOP)
  def pushUnknownValue(rreq: ResultReq): rreq.Result =
    rreq.ifResultNeeded {
      pushUnknownValue()
    }


  def buildUnknownCall(rreq: ResultReq, args: Seq[StackValue] = Seq.empty, anchor: ScalaDfaAnchor = null): rreq.Result = rreq.result {
    pop(args)
    val result = push(DfType.TOP, anchor)
    flushFields()
    maybeThrow()
    result
  }
}
