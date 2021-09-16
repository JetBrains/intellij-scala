package org.jetbrains.plugins.scala.lang.dfa.cfg

import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaValueFactory, DfaVariableValue, RelationType}
import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.lang.dfa.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

class ScalaDfaControlFlowBuilder(private val factory: DfaValueFactory, context: ScalaPsiElement) {

  private val flow = new ControlFlow(factory, context)
  private val trapTracker = new TrapTracker(factory, context)

  def build(): ControlFlow = {
    pushInstruction(new ReturnInstruction(factory, trapTracker.trapStack(), null))
    popReturnValue()
    flow.finish()
    flow
  }

  def pushInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  def pushUnknownValue(): Unit = pushInstruction(new PushValueInstruction(DfType.TOP))

  def pushUnknownCall(statement: ScBlockStatement, argCount: Int): Unit = {
    popArguments(argCount)

    val resultType = DfType.TOP // TODO collect more precise information on type
    pushInstruction(new PushValueInstruction(resultType, ScalaStatementAnchor(statement)))
    pushInstruction(new FlushFieldsInstruction)

    val transfer = trapTracker.maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    Option(transfer).foreach(transfer => pushInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer)))
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

  def createVariable(descriptor: ScalaVariableDescriptor): DfaVariableValue = factory.getVarFactory.createVariableValue(descriptor)
}
