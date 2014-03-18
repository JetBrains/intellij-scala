package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.{PsiElement, PsiMethod, JavaPsiFacade}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder

/**
 * @author Alefas
 * @since 28.02.12
 */
class StaticPsiTypedDefinitionWrapper(val typedDefinition: ScTypedDefinition,
                                       role: PsiTypedDefinitionWrapper.DefinitionRole.DefinitionRole,
                                       containingClass: PsiClassWrapper) extends {
  val elementFactory = JavaPsiFacade.getInstance(typedDefinition.getProject).getElementFactory
  val methodText = StaticPsiTypedDefinitionWrapper.methodText(typedDefinition, role, containingClass)
  val method: PsiMethod = {
    try {
      elementFactory.createMethodFromText(methodText, containingClass)
    } catch {
      case e: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }
} with LightMethodAdapter(typedDefinition.getManager, method, containingClass) with LightScalaMethod {
  override def getNavigationElement: PsiElement = typedDefinition

  override def canNavigate: Boolean = typedDefinition.canNavigate

  override def canNavigateToSource: Boolean = typedDefinition.canNavigateToSource

  override def getParent: PsiElement = containingClass
}

object StaticPsiTypedDefinitionWrapper {
  import PsiTypedDefinitionWrapper.DefinitionRole._

  def methodText(b: ScTypedDefinition, role: DefinitionRole, containingClass: PsiClassWrapper): String = {
    val builder = new StringBuilder

    ScalaPsiUtil.nameContext(b) match {
      case m: ScModifierListOwner =>
        builder.append(JavaConversionUtil.modifiers(m, true))
      case _ =>
    }

    val result = b.getType(TypingContext.empty)
    result match {
      case _ if role == SETTER || role == EQ => builder.append("void")
      case Success(tp, _) => builder.append(JavaConversionUtil.typeText(tp, b.getProject, b.getResolveScope))
      case _ => builder.append("java.lang.Object")
    }

    val qualName = containingClass.getQualifiedName
    val paramText = qualName.substring(0, qualName.length() - 6) + " This"

    builder.append(" ")
    val name = role match {
      case SIMPLE_ROLE => b.getName
      case GETTER => "get" + b.getName.capitalize
      case IS_GETTER => "is" + b.getName.capitalize
      case SETTER => "set" + b.getName.capitalize
      case EQ => b.getName + "_$eq"
    }
    builder.append(name)

    if (role != SETTER && role != EQ) {
      builder.append("(" + paramText + ")")
    } else {
      builder.append("(").append(paramText).append(", ")
      result match {
        case Success(tp, _) => builder.append(JavaConversionUtil.typeText(tp, b.getProject, b.getResolveScope))
        case _ => builder.append("java.lang.Object")
      }
      builder.append(" ").append(b.getName).append(")")
    }

    val holder = PsiTreeUtil.getContextOfType(b, classOf[ScAnnotationsHolder])
    if (holder != null) {
      builder.append(LightUtil.getThrowsSection(holder))
    }

    builder.append(" {}")

    builder.toString()
  }
}
