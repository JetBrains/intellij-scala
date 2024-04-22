package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.impl.light.LightParameter
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

class ScLightParameter(name: String, tpe: () => PsiType, scope: PsiElement, isVarargs: Boolean = false)
  extends LightParameter(name, NullPsiType, scope, scope.getLanguage, isVarargs) {

  @volatile
  private[this] var computedType: PsiType = NullPsiType

  override def getType: PsiType = {
    if (computedType == NullPsiType) {
      computedType = tpe()
    }

    computedType
  }
}

private object ScLightParameter {
  //this parameter for static forwarders in TraitName$class
  def fromThis(containingClass: PsiClassWrapper, scope: PsiElement): PsiParameter = {
    val originalTrait = containingClass.definition
    val result = new ScLightParameter("This", () => new PsiImmediateClassType(originalTrait, PsiSubstitutor.EMPTY), scope, isVarargs = false)

    result.setModifierList(ScLightModifierList.empty(containingClass.getManager))
    result
  }

  def from(param: ScParameter, substitutor: ScSubstitutor, isJavaVarargs: Boolean): PsiParameter = {
    implicit def elementScope: ElementScope = ElementScope(param)

    val isVarargs: Boolean = param.isRepeatedParameter && isJavaVarargs

    val result = new ScLightParameter(escapeJavaKeywords(param.getName), () => javaParameterType(param, substitutor, isVarargs), param.owner, isVarargs)

    result.setModifierList(ScLightModifierList(param))

    result
  }

  private def javaObjectType(project: Project) =
    JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT)

  private def javaFunction0Type(result: ScType)(implicit elementScope: ElementScope): PsiType = {
    result.typeSystem.toPsiType(FunctionType(result, Seq.empty), noPrimitives = true)
  }

  private def javaParameterType(param: ScParameter, substitutor: ScSubstitutor, isJavaVarargs: Boolean): PsiType = {
    implicit def elementScope: ElementScope = ElementScope(param)

    val paramType =
      if (isJavaVarargs) param.`type`()
      else param.getRealParameterType

    paramType.map(substitutor) match {
      case Right(tp) if param.isCallByNameParameter => javaFunction0Type(tp)
      case Right(tp) if isJavaVarargs               => new PsiEllipsisType(tp.toPsiType)
      case Right(tp)                                => tp.toPsiType
      case _                                        => javaObjectType(param.getProject)
    }
  }

  private def escapeJavaKeywords(name: String): String =
    if (PsiUtil.isKeyword(name, LanguageLevel.HIGHEST)) name + "$" else name
}
