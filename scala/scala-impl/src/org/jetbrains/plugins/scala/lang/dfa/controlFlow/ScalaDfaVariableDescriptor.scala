package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{isStableElement, scTypeToDfType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

case class ScalaDfaVariableDescriptor(variable: PsiElement, override val isStable: Boolean)
  extends JvmVariableDescriptor {

  override def toString: String = variable match {
    case namedElement: PsiNamedElement => namedElement.name
    case _ => "<unknown>"
  }

  override def getDfType(qualifier: DfaVariableValue): DfType = variable match {
    case typeable: Typeable => scTypeToDfType(typeable.`type`().getOrAny)
    case _ => DfType.TOP
  }
}

object ScalaDfaVariableDescriptor {

  def fromReferenceExpression(expression: ScReferenceExpression): Option[ScalaDfaVariableDescriptor] = {
    expression.getReference.bind()
      .map(_.element)
      .map(element => ScalaDfaVariableDescriptor(element, isStableElement(element)))
  }
}
