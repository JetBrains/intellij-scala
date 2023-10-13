package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.java.inst.PrimitiveConversionInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.SimpleAssignmentInstruction
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaVariableDescriptor
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.resolveExpressionType
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType

private trait TransformerUtils extends Transformer { this: ScalaPsiElementTransformer =>
  def assignVariableValue(descriptor: ScalaDfaVariableDescriptor,
                          valueExpression: Option[ScExpression],
                          definedType: ScType): Unit = {
    val dfaVariable = builder.createVariable(descriptor)
    val anchor = valueExpression.map(ScalaStatementAnchor(_)).orNull

    valueExpression match {
      case Some(expression) => transformExpression(expression)
        addImplicitConversion(Some(expression), Some(definedType))
      case _ => builder.pushUnknownValue()
    }

    builder.addInstruction(new SimpleAssignmentInstruction(anchor, dfaVariable))
  }

  def addImplicitConversion(expression: Option[ScExpression], balancedType: Option[ScType]): Unit = {
    val actualType = expression.map(resolveExpressionType)
    for (balancedType <- balancedType; actualType <- actualType) {
      if (actualType != balancedType) {
        balancedType.toPsiType match {
          case balancedPrimitiveType: PsiPrimitiveType =>
            builder.addInstruction(new PrimitiveConversionInstruction(balancedPrimitiveType, null))
          case _ =>
        }
      }
    }
  }
}
