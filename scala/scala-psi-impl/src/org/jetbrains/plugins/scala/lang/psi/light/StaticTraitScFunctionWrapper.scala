package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.light.StaticTraitScFunctionWrapper.methodName
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.AnyRef

class StaticTraitScFunctionWrapper(override val delegate: ScFunction, containingClass: PsiClassWrapper)
  extends PsiMethodWrapper(delegate, methodName(delegate), containingClass) {

  override protected def returnScType: ScType = {
    if (!delegate.isConstructor) delegate.returnType.getOrElse(AnyRef)
    else null
  }

  override protected def parameters: Seq[PsiParameter] = {
    val thisParam = ScLightParameter.fromThis(containingClass, delegate)
    thisParam +: delegate.effectiveParameterClauses.flatMap(_.effectiveParameters)
  }

  override protected def typeParameters: Seq[PsiTypeParameter] = Seq.empty

  override protected def modifierList: PsiModifierList =
    ScLightModifierList(delegate, isStatic = true)

  override def copy(): PsiElement =
    new StaticTraitScFunctionWrapper(delegate.copy().asInstanceOf[ScFunction], containingClass)
}

object StaticTraitScFunctionWrapper {

  def unapply(wrapper: StaticTraitScFunctionWrapper): Option[ScFunction] = Some(wrapper.delegate)

  def methodName(function: ScFunction): String =
    if (!function.isConstructor) function.getName
    else function.containingClass.getName
}
