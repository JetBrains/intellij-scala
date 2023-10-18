package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaClassDef
import com.intellij.codeInspection.dataFlow.java.inst.{JvmPushInstruction, ThrowInstruction}
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer
import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.{DeferredOffset, FixedOffset}
import com.intellij.codeInspection.dataFlow.lang.ir.{ConditionalGotoInstruction, ControlFlow, DupInstruction, EnsureInstruction, FlushFieldsInstruction, GotoInstruction, Instruction, PopInstruction, PushValueInstruction, ReturnInstruction, SimpleAssignmentInstruction, SpliceInstruction}
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValueFactory, DfaVariableValue, RelationType}
import com.intellij.psi.{CommonClassNames, PsiElement}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.{ScalaDfaAnchor, ScalaStatementAnchor}
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform._
import org.jetbrains.plugins.scala.lang.dfa.types.DfUnitType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._

final class ScalaDfaControlFlowBuilder(val analysedMethodInfo: AnalysedMethodInfo,
                                       private val factory: DfaValueFactory,
                                       context: ScalaPsiElement,
                                       val buildUnsupportedPsiElements: Boolean = true)
  extends DefinitionTransformation
    with ExpressionTransformation
    with StatementTransformation
    with InvocationTransformation
    with PatternMatchTransformation
    with TransformerUtils
    with specialSupport.CollectionAccessAssertionUtils
    with specialSupport.SpecialSyntheticMethodsTransformation
{
  private[controlFlow] implicit val rreqBuilderContext: ResultReq.BuilderContext = new ResultReq.BuilderContext(this)
  private val flow = new ControlFlow(factory, context)
  private val trapTracker = new TrapTracker(factory, JavaClassDef.typeConstraintFactory(context))

  /**
   * Finishes building of this control flow and returns its representation. It can be further
   * analysed using its ```toString``` method (which prints it in standard IR format)
   * or modules like [[DataFlowInterpreter]].
   *
   * @return [[com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow]] representation
   *         of instructions that have been pushed to this builder's stack.
   */
  def build(): ControlFlow = {
    addInstruction(new ReturnInstruction(factory, trapTracker.trapStack(), null))
    pop()
    flow.finish()
    flow
  }

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
    val finishOffset = new DeferredOffset
    addInstruction(new SimpleAssignmentInstruction(null, returnDestination))
    goto(finishOffset)

    anchorLabel(endOffset)
    pushUnknownValue()
    addInstruction(new SimpleAssignmentInstruction(null, returnDestination))

    anchorLabel(finishOffset)
    flow.finish()
    flow
  }

  def unsupported[R](element: ScalaPsiElement)(fallback: => R): R = {
    if (buildUnsupportedPsiElements) {
      fallback
    } else {
      throw TransformationFailedException(element, "Currently unsupported element")
    }
  }

  def flush(): Unit = addInstruction(new FlushFieldsInstruction)

  def addInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  def dup(): Unit = addInstruction(new DupInstruction)

  def push(dfType: DfType, anchor: DfaAnchor = null): Unit = addInstruction(new PushValueInstruction(dfType, anchor))

  def pushUnit(rreq: ResultReq = ResultReq.Required): Unit =
    rreq.ifResultNeeded {
      push(DfUnitType)
    }

  def pushUnknownValue(rreq: ResultReq = ResultReq.Required): Unit =
    rreq.ifResultNeeded {
      push(DfType.TOP)
    }

  def buildUnknownCall(argCount: Int, rreq: ResultReq, anchor: ScalaDfaAnchor = null): Unit = rreq.provideOne {
    pop(argCount)
    push(DfType.TOP, anchor)
    flush()

    val transfer = trapTracker.maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    Option(transfer).foreach { transfer =>
      ensureTos(RelationType.EQ, DfType.TOP, transfer)
    }
  }

  def pushVariable(descriptor: ScalaDfaVariableDescriptor, expression: ScExpression): Unit = {
    val dfaVariable = createVariable(descriptor)
    addInstruction(new JvmPushInstruction(dfaVariable, ScalaStatementAnchor(expression)))
  }

  def pop(times: Int = 1): Unit =
    if (times == 1) {
      addInstruction(new PopInstruction)
    } else if (times > 1) {
      addInstruction(new SpliceInstruction(times))
    }

  def goto(label: ControlFlow.ControlFlowOffset): Unit =
    addInstruction(new GotoInstruction(label))

  def gotoIfTosEquals(value: DfType, label: ControlFlow.ControlFlowOffset, @Nullable anchor: PsiElement): Unit =
    addInstruction(new ConditionalGotoInstruction(label, value, anchor))

  def gotoIfTosEquals(value: DfType, label: ControlFlow.ControlFlowOffset, anchor: Option[PsiElement] = None): Unit =
    gotoIfTosEquals(value, label, anchor.orNull)

  def newDeferredLabel(): DeferredOffset = new DeferredOffset

  def newLabelHere(): FixedOffset = new FixedOffset(flow.getInstructionCount)

  def anchorLabel(offset: DeferredOffset): Unit = offset.setOffset(flow.getInstructionCount)

  def finishElement(element: ScalaPsiElement): Unit = flow.finishElement(element)

  def ensureTos(rel: RelationType, compareTo: DfType, @Nullable transfer: DfaControlTransferValue = null, @Nullable problem: UnsatisfiedConditionProblem = null): Unit =
    addInstruction(new EnsureInstruction(problem, rel, compareTo, transfer))

  def createVariable(descriptor: ScalaDfaVariableDescriptor): DfaVariableValue = factory.getVarFactory.createVariableValue(descriptor)

  def maybeTransferValue(exceptionName: String): Option[DfaControlTransferValue] = Option(trapTracker.maybeTransferValue(exceptionName))

  def transferValue(transfer: ExceptionTransfer): DfaControlTransferValue = trapTracker.transferValue(transfer)

  def ret(expression: Option[ScExpression]): Unit =
    addInstruction(new ReturnInstruction(factory, trapTracker.trapStack(), expression.orNull))

  def throws(exceptionType: String, anchor: PsiElement): Unit =
    throws(trapTracker.transferValue(exceptionType), anchor)

  def throws(transfer: DfaControlTransferValue, anchor: PsiElement): Unit =
    addInstruction(new ThrowInstruction(transfer, anchor))

  def pushTrap(trap: DfaControlTransferValue.Trap): Unit = trapTracker.pushTrap(trap)
}