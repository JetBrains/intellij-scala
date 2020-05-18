package org.jetbrains.plugins.scala.lang
package psi

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeParamsRenderer
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ProjectContext

object PresentationUtil {

  def presentationStringForParameter(param: Parameter, substitutor: ScSubstitutor): String = {
    val builder = new StringBuilder
    builder.append(param.name)
    builder.append(": " + presentationStringForScalaType(param.paramType, substitutor))
    if (param.isRepeated) builder.append("*")
    if (param.isDefault) builder.append(" = _")
    builder.toString()
  }

  def presentationStringForScalaType(scType: ScType): String =
    presentationStringForScalaType(scType, ScSubstitutor.empty)

  def presentationStringForScalaType(scType: ScType, substitutor: ScSubstitutor): String =
    // empty context is used just because it was so before refactoring
    substitutor(scType).presentableText(TypePresentationContext.emptyContext)

  def presentationStringForJavaType(psiType: PsiType, substitutor: ScSubstitutor)
                                   (implicit project: ProjectContext): String =
    psiType match {
      case tp: PsiEllipsisType =>
        presentationStringForJavaType(tp.getComponentType, substitutor) + "*"
      case _         =>
        presentationStringForScalaType(psiType.toScType(), substitutor)
    }

  def presentationStringForPsiElement(element: ScalaPsiElement): String = {
    val substitutor = ScSubstitutor.empty
    val projectContext = element.projectContext
    val presentationContext = TypePresentationContext.emptyContext
    presentationStringForPsiElement(element, substitutor)(projectContext, presentationContext)
  }

  def presentationStringForPsiElement(element: PsiElement, substitutor: ScSubstitutor)
                                     (implicit project: ProjectContext, tpc: TypePresentationContext): String =
    element match {
      case clauses: ScParameters =>
        clauses.clauses.map(presentationStringForPsiElement(_, substitutor)).mkString("")
      case clause: ScParameterClause =>
        val buffer = new StringBuilder("")
        buffer.append("(")
        if (clause.isImplicit) buffer.append("implicit ")
        buffer.append(clause.parameters.map(presentationStringForPsiElement(_, substitutor)).mkString(", "))
        buffer.append(")")
        buffer.toString()
      case param: ScParameter =>
        ScalaPsiPresentationUtils.renderParameter(param)(presentationStringForScalaType(_, substitutor))
      case tp: ScTypeParamClause =>
        tp.typeParameters.map(t => presentationStringForPsiElement(t, substitutor)).mkString("[", ", ", "]")
      case param: ScTypeParam =>
        val boundsRenderer = new TypeParamsRenderer(stripContextTypeArgs = true)
        val result = boundsRenderer.render(param)(presentationStringForScalaType(_, substitutor))
        result
      case param: PsiTypeParameter =>
        val paramText = param.name
        //todo: possibly add supers and extends?
        paramText
      case params: PsiParameterList =>
        params.getParameters.map(presentationStringForPsiElement(_, substitutor)).mkString("(", ", ", ")")
      case param: PsiParameter =>
        val buffer: StringBuilder = new StringBuilder("")
        val list = param.getModifierList
        if (list == null) return ""
        val lastSize = buffer.length
        for (a <- list.getAnnotations) {
          if (lastSize != buffer.length) buffer.append(" ")
          val element = a.getNameReferenceElement
          if (element != null) buffer.append("@").append(element.getText)
        }
        if (lastSize != buffer.length) buffer.append(" ")
        val name = param.name
        if (name != null) {
          buffer.append(name)
        }
        buffer.append(": ")
        buffer.append(presentationStringForJavaType(param.getType, substitutor)) //todo: create param type, java.lang.Object => Any
        buffer.toString()
      case fun: ScFunction =>
        val buffer: StringBuilder = new StringBuilder("")
        fun.getParent match {
          case _: ScTemplateBody if fun.containingClass != null =>
            val qual = fun.containingClass.qualifiedName
            if (qual != null) {
              buffer.append(qual).append(".")
            }
          case _ =>
        }
        buffer.append(fun.name)
        fun.typeParametersClause match {
          case Some(tpc) =>
            // empty substitutor and context are used just because it was so before refactoring
            buffer.append(presentationStringForPsiElement(tpc, ScSubstitutor.empty)(project, TypePresentationContext.emptyContext))
          case _ =>
        }
        buffer.append(presentationStringForPsiElement(fun.paramClauses, substitutor)).append(": ")
        buffer.append(presentationStringForScalaType(fun.returnType.getOrAny, substitutor))
        buffer.toString()
      case _ =>
        element.getText
    }

  def accessModifierText(modifier: ScAccessModifier): String =
    ScalaPsiPresentationUtils.accessModifierText(modifier, fast = true)
}
