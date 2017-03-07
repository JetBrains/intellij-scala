package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.TextRange
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
class StaticPsiTypedDefinitionWrapper(val typedDefinition: ScTypedDefinition,
                                       role: PsiTypedDefinitionWrapper.DefinitionRole.DefinitionRole,
                                       containingClass: PsiClassWrapper) extends {
  val method: PsiMethod = {
    val methodText = StaticPsiTypedDefinitionWrapper.methodText(typedDefinition, role, containingClass)
    LightUtil.createJavaMethod(methodText, containingClass, typedDefinition.getProject)
  }

} with PsiMethodWrapper(typedDefinition.getManager, method, containingClass) {

  override def getNavigationElement: PsiElement = this

  override def navigate(requestFocus: Boolean): Unit = typedDefinition.navigate(requestFocus)

  override def canNavigate: Boolean = typedDefinition.canNavigate

  override def canNavigateToSource: Boolean = typedDefinition.canNavigateToSource

  override def getTextRange: TextRange = typedDefinition.getTextRange

  override def getTextOffset: Int = typedDefinition.getTextOffset

  override def getParent: PsiElement = containingClass

  override def isWritable: Boolean = getContainingFile.isWritable

  override protected def returnType: ScType = PsiTypedDefinitionWrapper.typeFor(typedDefinition, role)

  override protected def parameterListText: String = {
    PsiTypedDefinitionWrapper.parameterListText(typedDefinition, role, Some(containingClass))
  }
}

object StaticPsiTypedDefinitionWrapper {
  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  def methodText(b: ScTypedDefinition, role: DefinitionRole, containingClass: PsiClassWrapper): String = {
    val builder = new StringBuilder

    ScalaPsiUtil.nameContext(b) match {
      case m: ScModifierListOwner =>
        builder.append(JavaConversionUtil.annotationsAndModifiers(m, true))
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
