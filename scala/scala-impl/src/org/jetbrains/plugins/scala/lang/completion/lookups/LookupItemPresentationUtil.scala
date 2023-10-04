package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorator
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ProjectContext

private object LookupItemPresentationUtil {

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
    substitutor(scType).presentableText(TypePresentationContext.emptyContext)

  def presentationStringForJavaType(psiType: PsiType, substitutor: ScSubstitutor)
                                   (implicit project: ProjectContext): String =
    psiType match {
      case tp: PsiEllipsisType =>
        presentationStringForJavaType(tp.getComponentType, substitutor) + "*"
      case _ =>
        presentationStringForScalaType(psiType.toScType(), substitutor)
    }


  def presentationStringForPsiElement(element: PsiElement, substitutor: ScSubstitutor)
                                     (implicit project: ProjectContext): String = {
    def typeRenderer: TypeRenderer =
      presentationStringForScalaType(_, substitutor)

    def paramRenderer(typeRenderer: TypeRenderer = typeRenderer) = new ParameterRenderer(
      typeRenderer,
      ModifiersRenderer.SimpleText(TextEscaper.Html),
      new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateAll),
      textEscaper,
      withMemberModifiers = true,
      withAnnotations = true
    )

    def paramsRenderer: ParametersRenderer = new ParametersRenderer(
      paramRenderer(),
      shouldRenderImplicitModifier = true
    )

    def typeParamsRenderer(typeRenderer: TypeRenderer = typeRenderer) =
      new TypeParamsRenderer(typeRenderer, stripContextTypeArgs = true)

    def functionRenderer(typeRenderer: TypeRenderer = typeRenderer) =
      new FunctionRenderer(
        // empty substitutor for type parameters is used just because it was so before refactoring
        typeParamsRenderer(presentationStringForScalaType(_, ScSubstitutor.empty)),
        paramsRenderer,
        new TypeAnnotationRenderer(typeRenderer),
        renderDefKeyword = false
      ) {
        override def render(fun: ScFunction): String = {
          val qualifier = fun.getParent match {
            case _: ScTemplateBody => fun.containingClass.nullSafe.map(_.qualifiedName).map(_ + ".").getOrElse("")
            case _                 => ""
          }
          qualifier + super.render(fun)
        }
      }

    def renderPsiParameterList(substitutor: ScSubstitutor, params: PsiParameterList): String =
      params.getParameters.map(presentationStringForPsiElement(_, substitutor)).mkString("(", ", ", ")")

    element match {
      case fun: ScFunction             => functionRenderer().render(fun)
      case parameters: ScParameters    => paramsRenderer.renderClauses(parameters)
      case clause: ScParameterClause   => paramsRenderer.renderClause(clause)
      case param: ScParameter          => paramRenderer().render(param)
      case tpClause: ScTypeParamClause => typeParamsRenderer().render(tpClause)
      case param: ScTypeParam          => typeParamsRenderer().render(param)
      case param: PsiTypeParameter     => param.name
      case params: PsiParameterList    => renderPsiParameterList(substitutor, params)
      case param: PsiParameter         => renderPsiParameter(substitutor, param)
      case _                           => element.getText
    }
  }

  private def renderPsiParameter(substitutor: ScSubstitutor, param: PsiParameter)
                                (implicit project: ProjectContext): String = {
    val buffer: StringBuilder = new StringBuilder("")
    val list = param.getModifierList
    if (list == null)
      return ""
    val lastSize = buffer.length
    for (a <- list.getAnnotations) {
      if (lastSize != buffer.length) buffer.append(" ")
      val element = a.getNameReferenceElement
      if (element != null)
        buffer.append("@").append(element.getText)
    }
    if (lastSize != buffer.length)
      buffer.append(" ")
    val name = param.name
    if (name != null) {
      buffer.append(name)
    }
    buffer.append(": ")
    //todo: create param type, java.lang.Object => Any
    // (note from 2020: the comment is from 2010, it was left during refactorings, not sure what exactly is expected...)
    buffer.append(presentationStringForJavaType(param.getType, substitutor))
    buffer.toString
  }

  private def textEscaper: TextEscaper = TextEscaper.Html
}
