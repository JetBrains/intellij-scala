package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.{PsiElement, PsiMethod, JavaPsiFacade}
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, Success}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * @author Alefas
 * @since 28.02.12
 */
class StaticTraitScFunctionWrapper(val function: ScFunction, containingClass: PsiClassWrapper) extends {
  val elementFactory = JavaPsiFacade.getInstance(function.getProject).getElementFactory
  val methodText = StaticTraitScFunctionWrapper.methodText(function, containingClass: PsiClassWrapper)
  val method: PsiMethod = {
    try {
      elementFactory.createMethodFromText(methodText, containingClass)
    } catch {
      case e: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }
} with LightMethodAdapter(function.getManager, method, containingClass) with LightScalaMethod {
  override def getNavigationElement: PsiElement = function

  override def canNavigate: Boolean = function.canNavigate

  override def canNavigateToSource: Boolean = function.canNavigateToSource

  override def getParent: PsiElement = containingClass
}

object StaticTraitScFunctionWrapper {
  def methodText(function: ScFunction, containingClass: PsiClassWrapper): String = {
    val builder = new StringBuilder

    builder.append(JavaConversionUtil.modifiers(function, true))

    if (!function.isConstructor) {
      function.returnType match {
        case Success(tp, _) => builder.append(JavaConversionUtil.typeText(tp, function.getProject, function.getResolveScope))
        case _ => builder.append("java.lang.Object")
      }
    }

    builder.append(" ")
    val name = if (!function.isConstructor) function.getName else function.containingClass.getName
    builder.append(name)

    val qualName = containingClass.getQualifiedName
    builder.append(((qualName.substring(0, qualName.length() - 6) + " This") +: function.parameters.map { case param =>
      val builder = new StringBuilder
      param.getRealParameterType(TypingContext.empty) match {
        case Success(tp, _) =>
          if (param.isCallByNameParameter) builder.append("scala.Function0<")
          builder.append(JavaConversionUtil.typeText(tp, function.getProject, function.getResolveScope))
          if (param.isCallByNameParameter) builder.append(">")
        case _ => builder.append("java.lang.Object")
      }
      builder.append(" ").append(param.getName)
      builder.toString()
    }).mkString("(", ", ", ")"))

    builder.append(LightUtil.getThrowsSection(function))

    builder.append(" {}")

    builder.toString()
  }
}
