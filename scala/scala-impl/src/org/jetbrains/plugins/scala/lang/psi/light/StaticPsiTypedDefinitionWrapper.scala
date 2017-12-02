package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Alefas
  * @since 28.02.12
  */
class StaticPsiTypedDefinitionWrapper(val delegate: ScTypedDefinition,
                                      role: PsiTypedDefinitionWrapper.DefinitionRole.DefinitionRole,
                                      containingClass: PsiClassWrapper) extends {
  val method: PsiMethod = {
    val methodText = StaticPsiTypedDefinitionWrapper.methodText(delegate, role, containingClass)
    LightUtil.createJavaMethod(methodText, containingClass, delegate.getProject)
  }

} with PsiMethodWrapper(delegate.getManager, method, containingClass)
  with NavigablePsiElementWrapper[ScTypedDefinition] {

  override def isWritable: Boolean = getContainingFile.isWritable

  override def getPrevSibling: PsiElement = null

  override def getNextSibling: PsiElement = null

  override protected def returnType: ScType = PsiTypedDefinitionWrapper.typeFor(delegate, role)

  override protected def parameterListText: String = {
    PsiTypedDefinitionWrapper.parameterListText(delegate, role, Some(containingClass))
  }
}

object StaticPsiTypedDefinitionWrapper {

  def unapply(wrapper: StaticPsiTypedDefinitionWrapper): Option[ScTypedDefinition] = Some(wrapper.delegate)

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  def methodText(b: ScTypedDefinition, role: DefinitionRole, containingClass: PsiClassWrapper): String = {
    val builder = new StringBuilder

    ScalaPsiUtil.nameContext(b) match {
      case m: ScModifierListOwner =>
        builder.append(JavaConversionUtil.annotationsAndModifiers(m, isStatic = true))
      case _ =>
    }

    builder.append("java.lang.Object")

    builder.append(" ")
    val name = role match {
      case SIMPLE_ROLE => b.getName
      case GETTER => "get" + b.getName.capitalize
      case IS_GETTER => "is" + b.getName.capitalize
      case SETTER => "set" + b.getName.capitalize
      case EQ => b.getName + "_$eq"
    }
    builder.append(name)
    builder.append("()")

    val holder = PsiTreeUtil.getContextOfType(b, classOf[ScAnnotationsHolder])
    if (holder != null) {
      builder.append(LightUtil.getThrowsSection(holder))
    }

    builder.append(" {}")

    builder.toString()
  }
}
