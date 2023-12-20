package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement, PsiPackage}
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlBuilderWrapper
import org.jetbrains.plugins.scala.extensions.{Model, ObjectExt, PsiMemberExt, PsiNamedElementExt, StringExt, StringsExt}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{NameRenderer, TypePresentation, TypeRenderer}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ContextFunctionType, FunctionType, StdType, TupleType, TypeParameterType, WildcardType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScAndType, ScLiteralType, ScMatchType, ScOrType, ScParameterizedType, ScType, TypePresentationContext, api}

private [documentationProvider] class ScalaDocTypeRenderer(
  originalElement: Option[PsiElement],
  nameRenderer: NameRenderer,
  substitutor: Option[ScSubstitutor]
) extends TypeRenderer {
  private implicit val presentableContext: TypePresentationContext =
    originalElement.fold(TypePresentationContext.emptyContext)(TypePresentationContext.psiElementPresentationContext)

  override def render(typ: ScType): String = {
    substitutor.map(_.apply(typ)).getOrElse(typ) match {
      case stdType: StdType =>
        stdType.extractClass match {
          case Some(clazz) => nameRenderer.renderName(clazz)
          case _ => nameRenderer.escapeName(stdType.name)
        }
      case typeParam: TypeParameterType =>
        renderTypeName(typeParam.name, DefaultHighlighter.TYPEPARAM)
      case _: WildcardType =>
        renderTypeName("?", DefaultHighlighter.TYPEPARAM)
      case ScAbstractType(tpt, _, _) =>
        val name = tpt.name.capitalize + api.presentation.TypePresentation.ABSTRACT_TYPE_POSTFIX
        renderTypeName(name, DefaultHighlighter.TYPE_ALIAS)
      case FunctionType(ret, params) if !typ.isAliasType =>
        val paramsText = textOf(params)
        val name = s"$paramsText => ${render(ret)}"
        renderTypeName(name, DefaultHighlighter.TYPEPARAM)
      case ContextFunctionType(ret, params) if !typ.isAliasType =>
        val paramsText = textOf(params)
        val name = s"$paramsText ?=> ${render(ret)}"
        renderTypeName(name, DefaultHighlighter.TYPEPARAM)
      case ScThisType(element) =>
        element match {
          case clazz: ScTypeDefinition => nameRenderer.renderName(clazz) + ".this.type"
          case _ => "this.type"
        }
      case TupleType(comps) if !typ.isAliasType =>
        typesText(comps, Model.Parentheses)
      case ScDesignatorType(element) =>
        nameRenderer.renderName(element)
      case pte: ScParameterizedType =>
        render(pte.designator) + typesText(pte.typeArguments, Model.SquareBrackets)
      case ScAndType(lhs, rhs) =>
        s"${render(lhs)} & ${render(rhs)}"
      case ScOrType(lhs, rhs) =>
        s"${render(lhs)} | ${render(rhs)}"
      case mt@ScMethodType(retType, params, _) =>
        implicit val elementScope: ElementScope = mt.elementScope
        render(FunctionType(retType, params.map(_.paramType)))
      case ScLiteralType(value, _) =>
        nameRenderer.escapeName(value.presentation)
      case ScMatchType(scrutinee, cases) =>
        val scrutineeText = render(scrutinee)
        val caseTexts = cases.map(cs => "case " + cs._1 + " => " + cs._2)
        val parenthesesRequired = scrutinee.is[ScMatchType] || FunctionType.isFunctionType(scrutinee)
        (if (parenthesesRequired) scrutineeText.parenthesize() else scrutineeText)  + " match " + caseTexts.mkString("{ ", "; ", " }")
      case _ =>
        nameRenderer.escapeName(typ.presentableText)
    }
  }

  protected def renderTypeName(name: String, attrKey: TextAttributesKey): String = {
    val builder = new StringBuilder
    builder.appendAs(name, attrKey)
    builder.result()
  }

  private def textOf(params: Seq[ScType]): String = params match {
    case Seq(fun@FunctionType(_, _)) => render(fun).parenthesize()
    case Seq(tup@TupleType(_))       => render(tup).parenthesize()
    case Seq(mt: ScMatchType)        => render(mt).parenthesize()
    case Seq(head)                   => render(head)
    case _                           => typesText(params, Model.Parentheses)
  }

  private def typesText(types: Iterable[ScType], model: Model.Val = Model.None): String =
    types
      .map(render)
      .commaSeparated(model)
}

private [documentationProvider] object ScalaDocTypeRenderer {
  import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils._

  private val annotationsRenderer = new NameRenderer {
    override def renderName(e: PsiNamedElement): String = renderNameImpl(e)

    override def renderNameWithPoint(e: PsiNamedElement): String = {
      val res = renderNameImpl(e)
      if (res.nonEmpty) res + "." else res
    }

    private def renderNameImpl(e: PsiNamedElement): String =
      e match {
        case clazz: PsiClass =>
          clazz.qualifiedNameOpt
            .fold(escapeName(clazz.name))(_ => classLinkWithLabel(clazz, clazz.name, defLinkHighlight = false, isAnnotation = true))
        case _ =>
          psiElement(e, Some(e.name))
      }
  }

  private val nameRenderer: NameRenderer = new NameRenderer {
    override def escapeName(e: String): String = escapeHtml4(e)

    override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)

    override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

    private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils._
      val res = e match {
        case o: ScObject if withPoint && TypePresentation.isPredefined(o) => ""
        case _: PsiPackage if withPoint => ""
        case clazz: PsiClass =>
          clazz.qualifiedNameOpt
            .fold(escapeName(clazz.name))(_ => classLinkWithLabel(clazz, clazz.name, defLinkHighlight = false))
        case a: ScTypeAlias =>
          a.qualifiedNameOpt
            .fold(escapeName(e.name))(psiElementLink(_, e.name, attributesKey = Some(DefaultHighlighter.TYPE_ALIAS)))
        case _ =>
          psiElement(e, Some(e.name))
      }
      res
    }
  }

  private val quickInfoRenderer: NameRenderer = new NameRenderer {
    override def escapeName(e: String): String = escapeHtml4(e)

    override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)

    override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

    private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils._
      val res = e match {
        case o: ScObject if withPoint && TypePresentation.isPredefined(o) => ""
        case _: PsiPackage if withPoint => ""
        case clazz: PsiClass =>
          clazz.qualifiedNameOpt
            .fold(escapeName(clazz.name))(_ => classLinkWithLabel(clazz, clazz.name, defLinkHighlight = false))
        case a: ScTypeAlias =>
          a.qualifiedNameOpt
            .fold(escapeName(e.name))(psiElementLink(_, e.name, attributesKey = None))
        case _ =>
          escapeName(e.name)
      }
      res
    }
  }

  def apply(originalElement: Option[PsiElement]): TypeRenderer =
    new ScalaDocTypeRenderer(originalElement, nameRenderer, None)

  def forAnnotations(originalElement: Option[PsiElement]): TypeRenderer =
    new ScalaDocTypeRenderer(originalElement, annotationsRenderer, None)

  def forQuickInfo(originalElement: PsiElement, substitutor: ScSubstitutor): TypeRenderer =
    new ScalaDocTypeRenderer(Some(originalElement), quickInfoRenderer, Some(substitutor)) {
      override protected def renderTypeName(name: String, attrKey: TextAttributesKey): String = escapeHtml4(name)
    }
}
