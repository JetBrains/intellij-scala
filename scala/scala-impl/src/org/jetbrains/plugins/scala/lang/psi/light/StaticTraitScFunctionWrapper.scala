package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.AnyRef

/**
 * @author Alefas
 * @since 28.02.12
 */
class StaticTraitScFunctionWrapper(val function: ScFunction, containingClass: PsiClassWrapper) extends {
  val method: PsiMethod = {
    val methodText = StaticTraitScFunctionWrapper.methodText(function, containingClass: PsiClassWrapper)
    LightUtil.createJavaMethod(methodText, containingClass, function.getProject)
  }
} with PsiMethodWrapper(function.getManager, method, containingClass) {

  setNavigationElement(function)

  override def canNavigate: Boolean = function.canNavigate

  override def canNavigateToSource: Boolean = function.canNavigateToSource

  override protected def returnType: ScType = {
    if (!function.isConstructor) function.returnType.getOrElse(AnyRef)
    else null
  }

  override protected def parameterListText: String = {
    val qualName = containingClass.getQualifiedName
    val thisParam = qualName.stripSuffix("$class") + " This"
    (thisParam +: function.parameters.map(paramText)).mkString("(", ", ", ")")
  }

  private def paramText(param: ScParameter): String = {
    val paramAnnotations = JavaConversionUtil.annotations(param).mkString("", " ", " ")
    val typeText = param.getRealParameterType match {
      case Right(tp) =>
        val simple = JavaConversionUtil.typeText(tp)
        if (param.isCallByNameParameter) s"scala.Function0<$simple>"
        else simple
      case _ => "java.lang.Object"
    }
    val name = param.getName
    s"$paramAnnotations$typeText $name"
  }
}

object StaticTraitScFunctionWrapper {

  def unapply(wrapper: StaticTraitScFunctionWrapper): Option[ScFunction] = Some(wrapper.function)

  def methodText(function: ScFunction, containingClass: PsiClassWrapper): String = {
    val annotationsAndModifiers = JavaConversionUtil.annotationsAndModifiers(function, isStatic = true)
    val (retType, name) =
      if (!function.isConstructor) ("java.lang.Object", function.getName)
      else ("", function.containingClass.getName)

    val throwsSection = LightUtil.getThrowsSection(function)

    s"$annotationsAndModifiers$retType $name()$throwsSection {}"
  }
}
