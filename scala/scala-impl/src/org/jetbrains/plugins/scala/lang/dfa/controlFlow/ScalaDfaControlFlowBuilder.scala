package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaClassDef
import com.intellij.codeInspection.dataFlow.java.inst.JvmPushInstruction
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValueFactory, DfaVariableValue, RelationType}
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ScalaPsiElementTransformer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScExpression}

/**
 * Stack-based control flow builder for Scala, similar that supports analysis of dataflow.
 * It transforms Scala PSI elements into DFA-compatible Intermediate Representation, similar to Java bytecode.
 *
 * '''Usage:''' This builder can be either used directly manually or as a visitor to instances of
 * [[Transformable]].
 *
 * '''Visitor:''' To generate a control flow representation for a PSI element (or other syntactic construct),
 * wrap this element in a proper [[Transformable]]
 * instance. Then pass it an instance of this builder by calling ```transformable.transform(builder)```.
 * After that, call ```builder.build()``` to finalize building and collect the result.
 */
class ScalaDfaControlFlowBuilder(val analysedMethodInfo: AnalysedMethodInfo, private val factory: DfaValueFactory,
                                 context: ScalaPsiElement) {

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
    popReturnValue()
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

  def addInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  def pushUnknownValue(): Unit = addInstruction(new PushValueInstruction(DfType.TOP))

  def pushUnknownCall(statement: ScBlockStatement, argCount: Int): Unit = {
    popArguments(argCount)
    addInstruction(new PushValueInstruction(DfType.TOP, ScalaStatementAnchor(statement)))
    addInstruction(new FlushFieldsInstruction)

    val transfer = trapTracker.maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    Option(transfer).foreach(transfer =>
      addInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer)))
  }

  def pushVariable(descriptor: ScalaDfaVariableDescriptor, expression: ScExpression): Unit = {
    val dfaVariable = createVariable(descriptor)
    addInstruction(new JvmPushInstruction(dfaVariable, ScalaStatementAnchor(expression)))
  }

  def popReturnValue(): Unit = addInstruction(new PopInstruction)

  def popArguments(argCount: Int): Unit = {
    if (argCount > 1) {
      addInstruction(new SpliceInstruction(argCount))
    } else if (argCount == 1) {
      addInstruction(new PopInstruction)
    }
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
