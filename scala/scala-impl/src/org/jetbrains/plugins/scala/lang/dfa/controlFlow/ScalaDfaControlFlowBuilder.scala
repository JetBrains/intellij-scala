package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.inst.JvmPushInstruction
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValueFactory, DfaVariableValue, RelationType}
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.{ScalaPsiElementTransformer, Transformable}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScExpression}

/**
 * Stack-based bytecode-like control flow builder for Scala that supports analysis of dataflow.
 *
 * '''Usage:''' This builder can be either used directly manually or as a visitor to instances of
 * [[Transformable]].
 *
 * '''Visitor:''' To generate a control flow representation for a PSI element (or other syntactic construct),
 * wrap this element in a proper [[Transformable]]
 * instance. Then pass it an instance of this builder by calling ```transformable.transform(builder)```.
 * After that, call ```builder.build()``` to finalize building and collect the result.
 *
 * @author Gerard Dróżdż
 */
class ScalaDfaControlFlowBuilder(private val factory: DfaValueFactory, context: ScalaPsiElement) {

  private val flow = new ControlFlow(factory, context)
  private val trapTracker = new TrapTracker(factory, context)

  /**
   * Finishes building of this control flow and returns its representation. It can be further
   * analysed using its ```toString``` method (which prints it in standard IR format)
   * or modules like [[DataFlowInterpreter]].
   *
   * @return [[com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow]] representation
   *         of instructions that have been pushed to this builder's stack.
   */
  def build(): ControlFlow = {
    pushInstruction(new ReturnInstruction(factory, trapTracker.trapStack(), null))
    popReturnValue()
    flow.finish()
    flow
  }

  def buildAndReturn(returnDestination: DfaVariableValue): ControlFlow = {
    pushInstruction(new SimpleAssignmentInstruction(null, returnDestination))
    flow.finish()
    flow
  }

  def pushInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  def pushUnknownValue(): Unit = pushInstruction(new PushValueInstruction(DfType.TOP))

  def pushUnknownCall(statement: ScBlockStatement, argCount: Int): Unit = {
    popArguments(argCount)
    pushInstruction(new PushValueInstruction(DfType.TOP, ScalaStatementAnchor(statement)))
    pushInstruction(new FlushFieldsInstruction)

    val transfer = trapTracker.maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    Option(transfer).foreach(transfer =>
      pushInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer)))
  }

  def pushVariable(descriptor: ScalaDfaVariableDescriptor, expression: ScExpression): Unit = {
    val dfaVariable = createVariable(descriptor)
    pushInstruction(new JvmPushInstruction(dfaVariable, ScalaStatementAnchor(expression)))
  }

  def assignVariableValue(descriptor: ScalaDfaVariableDescriptor, valueExpression: Option[ScExpression]): Unit = {
    val dfaVariable = createVariable(descriptor)
    val anchor = valueExpression.map(ScalaStatementAnchor(_)).orNull

    valueExpression match {
      case Some(element) => new ScalaPsiElementTransformer(element).transform(this)
      case _ => pushUnknownValue()
    }

    pushInstruction(new SimpleAssignmentInstruction(anchor, dfaVariable))
  }

  def popReturnValue(): Unit = pushInstruction(new PopInstruction)

  def popArguments(argCount: Int): Unit = {
    if (argCount > 1) {
      pushInstruction(new SpliceInstruction(argCount))
    } else if (argCount == 1) {
      pushInstruction(new PopInstruction)
    }
  }

  def setOffset(offset: DeferredOffset): Unit = offset.setOffset(flow.getInstructionCount)

  def finishElement(element: ScalaPsiElement): Unit = flow.finishElement(element)

  def createVariable(descriptor: ScalaDfaVariableDescriptor): DfaVariableValue = factory.getVarFactory.createVariableValue(descriptor)

  def maybeTransferValue(exceptionName: String): Option[DfaControlTransferValue] = Option(trapTracker.maybeTransferValue(exceptionName))
}
