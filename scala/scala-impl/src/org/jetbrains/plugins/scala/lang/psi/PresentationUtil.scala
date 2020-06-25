package org.jetbrains.plugins.scala.lang
package psi

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorateOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{TextEscaper, _}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ProjectContext

@deprecated(
  """Do not use this generic class!
    |It has unclear purpose and used in a lot of different subsystems.
    |All these subsystems can have different requirements and bounding them all to this single utility class is fragile.
    |Use more specific renderers from org.jetbrains.plugins.scala.lang.psi.types.api.presentation package""".stripMargin
)
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
    presentationStringForPsiElement(element, substitutor)(projectContext)
  }

  def presentationStringForPsiElement(element: PsiElement, substitutor: ScSubstitutor)
                                     (implicit project: ProjectContext): String = {
    def typeRenderer: TypeRenderer =
      presentationStringForScalaType(_, substitutor)

    def paramRenderer(typeRenderer: TypeRenderer = typeRenderer) = new ParameterRenderer(
      typeRenderer,
      ModifiersRenderer.SimpleText(TextEscaper.Html),
      new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorateOptions.DecorateAll),
      textEscaper,
      withMemberModifiers = true,
      withAnnotations = true
    )

    def paramsRenderer: ParametersRenderer = new ParametersRenderer(
      paramRenderer(),
      renderImplicitModifier = true,
      clausesSeparator = ""
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

    element match {
      case fun: ScFunction             => functionRenderer().render(fun)
      case parameters: ScParameters    => paramsRenderer.renderClauses(parameters)
      case clause: ScParameterClause   => paramsRenderer.renderClause(clause)
      case param: ScParameter          => paramRenderer().render(param)
      case tpClause: ScTypeParamClause => typeParamsRenderer().render(tpClause)
      case param: ScTypeParam          => typeParamsRenderer().render(param)
      case param: PsiTypeParameter     => param.name
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
      case _ =>
        element.getText
    }
  }

  def accessModifierText(modifier: ScAccessModifier): String =
    new AccessModifierRenderer(new AccessQualifierRenderer.SimpleText(textEscaper)).render(modifier)

  private def textEscaper: TextEscaper = TextEscaper.Html
}
