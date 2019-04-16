package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.{PsiModifierList, PsiParameter, PsiTypeParameter}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

class ScPrimaryConstructorWrapper(override val delegate: ScPrimaryConstructor, isJavaVarargs: Boolean = false)
  extends PsiMethodWrapper(delegate, delegate.containingClass.getName, delegate.containingClass) {

  override protected def returnScType: ScType = null

  protected def parameters: Seq[PsiParameter] =
    delegate.effectiveParameterClauses
      .flatMap(_.effectiveParameters)
      .map(ScLightParameter.from(_, ScSubstitutor.empty, isJavaVarargs))

  protected def typeParameters: Seq[PsiTypeParameter] = delegate.getTypeParameters

  override def isWritable: Boolean = getContainingFile.isWritable

  override def isVarArgs: Boolean = isJavaVarargs

  protected def modifierList: PsiModifierList = ScLightModifierList(delegate)

  override def isConstructor: Boolean = true
}

object ScPrimaryConstructorWrapper {

  def unapply(wrapper: ScPrimaryConstructorWrapper): Option[ScPrimaryConstructor] = Some(wrapper.delegate)
}