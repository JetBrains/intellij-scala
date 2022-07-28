package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType

class StaticPsiTypedDefinitionWrapper(override val delegate: ScTypedDefinition,
                                      role: DefinitionRole,
                                      containingClass: PsiClassWrapper)
  extends PsiMethodWrapper(delegate, javaMethodName(delegate.name, role), containingClass) {

  override def isWritable: Boolean = getContainingFile.isWritable

  override def getPrevSibling: PsiElement = null

  override def getNextSibling: PsiElement = null

  override protected def returnScType: ScType = PsiTypedDefinitionWrapper.typeFor(delegate, role)

  override protected def parameters: Seq[PsiParameter] = PsiTypedDefinitionWrapper.propertyMethodParameters(delegate, role, Some(containingClass))

  override protected def typeParameters: Seq[PsiTypeParameter] = Seq.empty

  override protected def modifierList: PsiModifierList =
    ScLightModifierList(delegate, isStatic = true)

  override def copy(): PsiElement =
    new StaticPsiTypedDefinitionWrapper(delegate.copy().asInstanceOf[ScTypedDefinition], role, containingClass)
}

object StaticPsiTypedDefinitionWrapper {

  def unapply(wrapper: StaticPsiTypedDefinitionWrapper): Option[ScTypedDefinition] = Some(wrapper.delegate)
}
