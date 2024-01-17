package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{isStableElement, scTypeToDfType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

case class ScalaDfaVariableDescriptor(variable: PsiElement,
                                      qualifier: Option[ScalaDfaVariableDescriptor],
                                      override val isStable: Boolean) extends JvmVariableDescriptor {

  override def toString: String = (qualifier, variable) match {
    case (Some(qualifierVariable), namedElement: PsiNamedElement) =>
      s"$qualifierVariable.${namedElement.name}"
    case (None, namedElement: PsiNamedElement) => namedElement.name
    case _ => "<unknown>"
  }

  override def getDfType(qualifier: DfaVariableValue): DfType = variable match {
    case typeable: Typeable => scTypeToDfType(typeable.`type`().getOrAny)
    case _ => DfType.TOP
  }
}

object ScalaDfaVariableDescriptor {

  def fromReferenceExpression(expression: ScReferenceExpression): Option[ScalaDfaVariableDescriptor] = {
    val qualifierVariable = expression.qualifier match {
      case Some(reference: ScReferenceExpression) => fromReferenceExpression(reference)
      case _ => None
    }

    if (expression.isQualified && qualifierVariable.isEmpty) None
    else expression.getReference.bind()
      .map(_.element)
      .map(element => ScalaDfaVariableDescriptor(element, qualifierVariable,
        isStableElement(element) && qualifierVariable.forall(qualifier => isStableElement(qualifier.variable))))
  }
}
