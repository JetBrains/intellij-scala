package org.jetbrains.plugins.scala.lang.dfa.controlFlow

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.{PsiElement, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.ScalaNil
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{dfTypeCollectionOfSize, isStableElement, scTypeToDfType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

case class ScalaDfaVariableDescriptor(variable: PsiElement, override val isStable: Boolean)
  extends JvmVariableDescriptor {

  override def toString: String = variable match {
    case namedElement: PsiNamedElement => namedElement.name
    case _ => "<unknown>"
  }

  override def getDfType(qualifier: DfaVariableValue): DfType = {
    val basicType = variable match {
      case typeable: Typeable => scTypeToDfType(typeable.`type`().getOrAny)
      case _ => DfType.TOP
    }

    findEnhancedDfType match {
      case Some(dfType) => basicType.meet(dfType)
      case None => basicType
    }
  }

  private def findEnhancedDfType: Option[DfType] = {
    val qualifiedName = variable match {
      case member: PsiNamedElement with PsiMember => member.qualifiedNameOpt
      case _ => None
    }

    qualifiedName match {
      case Some(ScalaNil) => Some(dfTypeCollectionOfSize(0))
      case _ => None
    }
  }
}

object ScalaDfaVariableDescriptor {

  def fromReferenceExpression(expression: ScReferenceExpression): Option[ScalaDfaVariableDescriptor] = {
    expression.getReference.bind()
      .map(_.element)
      .map(element => ScalaDfaVariableDescriptor(element, isStableElement(element)))
  }
}
