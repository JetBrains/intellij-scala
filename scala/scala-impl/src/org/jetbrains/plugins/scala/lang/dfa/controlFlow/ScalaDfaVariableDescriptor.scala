package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{isStableElement, scTypeToDfType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

case class ScalaDfaVariableDescriptor(variable: PsiElement, qualifier: Option[PsiElement], override val isStable: Boolean)
  extends JvmVariableDescriptor {

  override def toString: String = (qualifier, variable) match {
    case (Some(qualifierExpression), namedElement: PsiNamedElement) =>
      s"${qualifierExpression.getText}.${namedElement.name}"
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
    val qualifier = expression.qualifier match {
      case Some(reference: ScReferenceExpression) => reference.bind().map(_.element)
      case _ => None
    }

    expression.getReference.bind()
      .map(_.element)
      .map(element => ScalaDfaVariableDescriptor(element, qualifier,
        isStableElement(element) && qualifier.forall(isStableElement)))
  }
}
