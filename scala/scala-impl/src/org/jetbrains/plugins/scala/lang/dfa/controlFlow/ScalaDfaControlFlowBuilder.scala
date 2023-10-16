package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaClassDef
import com.intellij.codeInspection.dataFlow.java.inst.JvmPushInstruction
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, EnsureInstruction, FlushFieldsInstruction, GotoInstruction, Instruction, PopInstruction, PushValueInstruction, ReturnInstruction, SimpleAssignmentInstruction, SpliceInstruction}
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValueFactory, DfaVariableValue, RelationType}
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.{DefinitionTransformation, ExpressionTransformation, InvocationTransformation, ScalaPsiElementTransformation, TransformerUtils, specialSupport}
import org.jetbrains.plugins.scala.lang.dfa.types.DfUnitType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaDfaControlFlowBuilder(val analysedMethodInfo: AnalysedMethodInfo,
                                 private val factory: DfaValueFactory,
                                 context: ScalaPsiElement)
  extends DefinitionTransformation
    with ExpressionTransformation
    with InvocationTransformation
    with TransformerUtils
    with ScalaPsiElementTransformation
    with specialSupport.CollectionAccessAssertionUtils
    with specialSupport.SpecialSyntheticMethodsTransformation
{

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
    addInstruction(new GotoInstruction(finishOffset))

    setOffset(endOffset)
    pushUnknownValue()
    addInstruction(new SimpleAssignmentInstruction(null, returnDestination))

    setOffset(finishOffset)
    flow.finish()
    flow
  }

  def flush(): Unit = addInstruction(new FlushFieldsInstruction)

  def addInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  def push(dfType: DfType, anchor: DfaAnchor = null): Unit = addInstruction(new PushValueInstruction(dfType, anchor))

  def pushUnit(): Unit = push(DfUnitType)

  def pushUnknownValue(): Unit = push(DfType.TOP)

  def pushUnknownCall(statement: ScBlockStatement, argCount: Int): Unit = {
    pop(argCount)
    push(DfType.TOP, ScalaStatementAnchor(statement))
    flush()

    val transfer = trapTracker.maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    Option(transfer).foreach(transfer =>
      addInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer)))
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

  def setOffset(offset: DeferredOffset): Unit = offset.setOffset(flow.getInstructionCount)

  def finishElement(element: ScalaPsiElement): Unit = flow.finishElement(element)

  def createVariable(descriptor: ScalaDfaVariableDescriptor): DfaVariableValue = factory.getVarFactory.createVariableValue(descriptor)

  def maybeTransferValue(exceptionName: String): Option[DfaControlTransferValue] = Option(trapTracker.maybeTransferValue(exceptionName))

  def transferValue(transfer: ExceptionTransfer): DfaControlTransferValue = trapTracker.transferValue(transfer)

  def addReturnInstruction(expression: Option[ScExpression]): Unit = {
    addInstruction(new ReturnInstruction(factory, trapTracker.trapStack(), expression.orNull))
  }

  def pushTrap(trap: DfaControlTransferValue.Trap): Unit = trapTracker.pushTrap(trap)
}