package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder.StackValue
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait TransformerUtils { this: ScalaDfaControlFlowBuilder =>
  def assignVariableValue(descriptor: ScalaDfaVariableDescriptor,
                          valueExpression: Option[ScExpression],
                          definedType: ScType): Unit = {
    val dfaVariable = createVariable(descriptor)
    val anchor = valueExpression.map(ScalaStatementAnchor(_)).orNull

    val value = valueExpression match {
      case Some(expression) =>
        transformExpression(expression, ResultReq.Required)
        //buildImplicitConversion(Some(expression), Some(definedType))
      case _ =>
        pushUnknownValue()
    }

    assign(dfaVariable, value, anchor)
  }

  def convertPrimitiveIfNeeded(value: StackValue, fromType: Option[ScType], toType: Option[ScType]): StackValue = {
    (fromType, toType) match {
      case (Some(fromType), Some(toType)) =>
        toType.toPsiType match {
          case prim: PsiPrimitiveType if prim != fromType.toPsiType =>
            convertPrimitive(value, prim)
          case _ =>
            value
        }
      case _ =>
        value
    }
  }
}
