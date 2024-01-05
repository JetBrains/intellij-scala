package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement, PsiPackage}
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlBuilderWrapper
import org.jetbrains.plugins.scala.extensions.{Model, ObjectExt, PsiMemberExt, PsiNamedElementExt, StringExt, StringsExt}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.parsing.Associativity
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.operatorAssociativity
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation.TypeLambdaArrowWithSpaces
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation.ABSTRACT_TYPE_POSTFIX
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{NameRenderer, TypeBoundsRenderer, TypePresentation, TypeRenderer}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ContextFunctionType, FunctionType, ParameterizedType, StdType, TupleType, TypeParameter, TypeParameterType, WildcardType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScAndType, ScExistentialArgument, ScExistentialType, ScLiteralType, ScMatchType, ScOrType, ScParameterizedType, ScType, TypePresentationContext, api}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

private [documentationProvider] class ScalaDocTypeRenderer(
  originalElement: Option[PsiElement],
  nameRenderer: NameRenderer,
  substitutor: Option[ScSubstitutor]
)(implicit projectContext: ProjectContext) extends TypeRenderer {
  private lazy val boundsRenderer = new TypeBoundsRenderer(nameRenderer)

  private implicit val presentableContext: TypePresentationContext =
    originalElement.fold(TypePresentationContext.emptyContext)(TypePresentationContext.psiElementPresentationContext)

  /**
    * The following code is based on [[org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation]].
    * It's simplified, as here we are only interested in types declarations, and broken into a collection of shorter methods.
    */
  override def render(typ: ScType): String = substitutor.map(_.apply(typ)).getOrElse(typ) match {
    case stdType: StdType =>
      stdType.extractClass match {
        case Some(clazz) => nameRenderer.renderName(clazz)
        case _           => nameRenderer.escapeName(stdType.name)
      }
    case typeParam: TypeParameterType =>
      renderTypeName(typeParam.name, DefaultHighlighter.TYPEPARAM)
    case _: WildcardType =>
      renderTypeName("?", DefaultHighlighter.TYPEPARAM)
    case ScAbstractType(tpt, _, _) =>
      renderTypeName(tpt.name.capitalize + ABSTRACT_TYPE_POSTFIX, DefaultHighlighter.TYPE_ALIAS)
    case FunctionType(ret, params) if !typ.isAliasType =>
      s"${textOf(params)} ${ScalaPsiUtil.functionArrow} ${render(ret)}"
    case ContextFunctionType(ret, params) if !typ.isAliasType =>
      s"${textOf(params)} ${ScalaPsiUtil.contextFunctionArrow} ${render(ret)}"
    case ScThisType(clazz: ScTypeDefinition) =>
      nameRenderer.renderName(clazz) + ".this.type"
    case ScThisType(_) =>
      "this.type"
    case TupleType(comps) if !typ.isAliasType =>
      typesText(comps, Model.Parentheses)
    case ScDesignatorType(element) =>
      nameRenderer.renderName(element)
    case p: ParameterizedType =>
      parameterizedTypeText(p)(render)
    case ScAndType(lhs, rhs) =>
      s"${render(lhs)} & ${render(rhs)}"
    case ScOrType(lhs, rhs) =>
      s"${render(lhs)} | ${render(rhs)}"
    case mt@ScMethodType(retType, params, _) =>
      render(FunctionType(retType, params.map(_.paramType))(mt.elementScope))
    case ScLiteralType(value, _) =>
      nameRenderer.escapeName(value.presentation)
    case ScMatchType(scrutinee, cases) =>
      scrutineeText(scrutinee, cases)
    case p: ScProjectionType =>
      nameRenderer.renderName(p.actualElement)
    case ex: ScExistentialType =>
      existentialTypeText(ex, checkWildcard = true)
    case pt@ScTypePolymorphicType(internalType, typeParameters) =>
      typeLambdaText(internalType, typeParameters, pt.isLambdaTypeElement)
    case _ =>
      nameRenderer.escapeName(typ.presentableText)
  }

  private def typeLambdaText(internalType: ScType, typeParameters: Seq[TypeParameter], isLambdaTypeElement: Boolean) = {
    val typeParametersTexts = typeParameters.map {
      case TypeParameter(parameter, _, lowerType, upperType) =>
        renderTypeName(parameter.name, DefaultHighlighter.TYPEPARAM) +
          boundsRenderer.lowerBoundText(lowerType)(render) +
          boundsRenderer.upperBoundText(upperType)(render)
    }
    val typeParametersText = typeParametersTexts.commaSeparated(model = Model.SquareBrackets)
    // TODO Custom lambda and polymorphic function types, SCL-20394
    val separator =
      if (isLambdaTypeElement) ScalaTokenType.TypeLambdaArrow.toString
      else if (FunctionType.isFunctionType(internalType)) ScalaPsiUtil.functionArrow
      else ""
    s"$typeParametersText $separator ${render(internalType)}"
  }

  private def scrutineeText(scrutinee: ScType, cases: Seq[(ScType, ScType)]) = {
    val text = render(scrutinee)
    val textWrapped =
      if (scrutinee.is[ScMatchType] || FunctionType.isFunctionType(scrutinee)) text.parenthesize() else text
    val caseText = cases.map(cs => s"case ${cs._1} ${ScalaPsiUtil.functionArrow} ${cs._2}").mkString("{ ", "; ", " }")
    s"$textWrapped match $caseText"
  }

  private def namedExistentials(wildcards: Seq[ScExistentialArgument]) =
    wildcards.map { wildcard =>
      existentialArgWithBounds(wildcard, s"type ${wildcard.name}")
    }.mkString(" forSome {", "; ", "}")

  private def placeholder(wildcard: ScExistentialArgument) =
    existentialArgWithBounds(wildcard, if (presentableContext.compoundTypeWithAndToken) "?" else "_")

  private  def existentialArgWithBounds(wildcard: ScExistentialArgument, name: String): String = {
    val argsText = wildcard.typeParameters.map(_.name) match {
      case Seq() => ""
      case parameters => parameters.commaSeparated(model = Model.SquareBrackets)
    }

    val lowerBound = boundsRenderer.lowerBoundText(wildcard.lower)(this)
    val upperBound = boundsRenderer.upperBoundText(wildcard.upper)(this)
    s"$name$argsText$lowerBound$upperBound"
  }

  @tailrec
  private def existentialTypeText(existentialType: ScExistentialType, checkWildcard: Boolean): String = existentialType match {
    case ScExistentialType(q, Seq(w)) if checkWildcard && q == w =>
      placeholder(w)
    case ScExistentialType(_, Seq(_)) if checkWildcard =>
      existentialTypeText(existentialType, checkWildcard = false)
    case ScExistentialType(quant @ ParameterizedType(_, typeArgs), wildcards) =>
      val usedMoreThanOnce = ScExistentialArgument.usedMoreThanOnce(quant)

      def mayBePlaceholder(arg: ScExistentialArgument): Boolean =
        !usedMoreThanOnce(arg) && typeArgs.contains(arg) && arg.typeParameters.isEmpty

      val (placeholders, namedWildcards) = wildcards.partition(mayBePlaceholder)

      val prefix = parameterizedTypeText(quant) {
        case arg: ScExistentialArgument if placeholders.contains(arg) => placeholder(arg)
        case arg: ScExistentialArgument => arg.name
        case t => render(t)
      }

      if (namedWildcards.isEmpty) prefix else s"($prefix)${namedExistentials(namedWildcards)}"
    case ex: ScExistentialType =>
      s"(${render(ex.quantified)})${namedExistentials(ex.wildcards)}"
  }

  private def parameterizedTypeText(p: ParameterizedType)(printArgsFun: ScType => String): String = p match {
    case ParameterizedType(ScalaDocTypeRenderer.InfixDesignator(op), Seq(left, right)) =>
      infixTypeText(op, left, right, printArgsFun(_))
    case ParameterizedType(des, typeArgs) =>
      render(des) + typeArgs.map(printArgsFun(_)).commaSeparated(model = Model.SquareBrackets)
  }

  def infixTypeText(op: PsiNamedElement, left: ScType, right: ScType, printArgsFun: ScType => String): String = {
    val assoc = operatorAssociativity(op.name)

    def componentText(`type`: ScType, requiredAssoc: Associativity.LeftOrRight) = {
      val needParenthesis = `type` match {
        case ParameterizedType(ScalaDocTypeRenderer.InfixDesignator(newOp), _) =>
          assoc != operatorAssociativity(newOp.name) || assoc == requiredAssoc
        case _ => false
      }

      printArgsFun(`type`).parenthesize(needParenthesis)
    }

    s"${componentText(left, Associativity.Right)} ${nameRenderer.renderName(op)} ${componentText(right, Associativity.Left)}"
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

  object InfixDesignator {
    private[this] val showAsInfixAnnotation: String = "scala.annotation.showAsInfix"

    private def mayUseSimpleName(named: PsiNamedElement)(implicit context: TypePresentationContext): Boolean = {
      val simpleName = named.name
      simpleName == nameRenderer.renderName(named) || context.nameResolvesTo(simpleName, named)
    }

    private def annotated(named: PsiNamedElement) = named match {
      case c: PsiClass => c.getAnnotations.map(_.getQualifiedName).contains(showAsInfixAnnotation)
      case _           => false
    }

    def unapply(des: ScType)(implicit context: TypePresentationContext): Option[PsiNamedElement] =
      des.extractDesignated(expandAliases = false)
        .filter(named => mayUseSimpleName(named) && (annotated(named) || ScalaNamesUtil.isOperatorName(named.name)))
  }

  private val annotationsRenderer = new NameRenderer {
    override def renderName(e: PsiNamedElement): String = renderNameImpl(e)

    override def renderNameWithPoint(e: PsiNamedElement): String = {
      val res = renderNameImpl(e)
      if (res.nonEmpty) s"$res." else res
    }

    private def renderNameImpl(e: PsiNamedElement): String = e match {
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

    private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = e match {
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
  }

  private val quickInfoRenderer: NameRenderer = new NameRenderer {
    override def escapeName(e: String): String = escapeHtml4(e)

    override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)

    override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

    private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = e match {
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
  }

  def apply(originalElement: Option[PsiElement])(implicit projectContext: ProjectContext): TypeRenderer =
    new ScalaDocTypeRenderer(originalElement, nameRenderer, None)

  def forAnnotations(originalElement: Option[PsiElement])(implicit projectContext: ProjectContext): TypeRenderer =
    new ScalaDocTypeRenderer(originalElement, annotationsRenderer, None)

  def forQuickInfo(originalElement: PsiElement, substitutor: ScSubstitutor)(implicit projectContext: ProjectContext): TypeRenderer =
    new ScalaDocTypeRenderer(Some(originalElement), quickInfoRenderer, Some(substitutor)) {
      override protected def renderTypeName(name: String, attrKey: TextAttributesKey): String = escapeHtml4(name)
    }
}
