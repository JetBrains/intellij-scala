package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.NlsContexts.HintText
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.editor.documentationProvider.renderers.{ScalaDocTypeRenderer, WithHtmlPsiLink}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{ContextBoundInfo, inNameContext}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScNamedTuplePattern, ScNamedTuplePatternComponent, ScNamingPattern, ScParenthesisedPattern, ScPattern, ScPatterns, ScTuplePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScNamedTupleComponent, ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNamedTuple, ScParenthesisedExpr, ScTuple}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorator
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

// TODO 1: analyze performance and whether rendered info is cached?
// TODO 2:  (!) quick info on the element itself should lead to "Show find usages" tooltip, no to quick info tooltip
//  (unify with Java behaviour)
// TODO 3: add minimum required module/location, if class/method is in same scope, do not render module/location at all
object ScalaDocQuickInfoGenerator {
  @HintText
  def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): Option[String] = {
    val substitutor = originalElement match {
      case ref: ScReference =>
        ref.bind() match {
          case Some(ScalaResolveResult(_, subst)) => subst
          case _ => ScSubstitutor.empty
        }
      case _ => ScSubstitutor.empty
    }
    getQuickNavigateInfo(element, originalElement, substitutor)
  }

  @HintText
  def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement, substitutor: ScSubstitutor): Option[String] = {
    val typeRenderer: TypeRenderer = ScalaDocTypeRenderer.forQuickInfo(originalElement, substitutor)(ProjectContext.fromPsi(element))
    val generator = new ScalaDocQuickInfoGenerator(typeRenderer)
    generator.getQuickNavigateInfo(element)
  }
}

class ScalaDocQuickInfoGenerator(
  typeRenderer: TypeRenderer
) {

  //TODO: not supported yet
  private[documentationProvider] val EnableSyntaxHighlightingInQuickInfo = false

  private val modifiersRenderer: ModifiersRendererLike = (buffer, modListOwner) => {
    import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps
    modListOwner.getModifierList.modifiers.foreach { m =>
      buffer.append(m.text).append(" ")
    }
  }

  private val typeParamsRenderer: TypeParamsRenderer =
    new TypeParamsRenderer(typeRenderer, TextEscaper.Html, stripContextTypeArgs = false)

  private val parameterTypeDecoratorFull = new ParameterTypeDecorator(
    showByNameArrow = true,
    showDefaultValue = true
  ) {
    private val Ellipsis = '…'

    override protected def renderDefaultValue(buffer: StringBuilder, param: ScParameter): Unit = {
      param.getDefaultExpressionInSource match {
        case Some(expr) =>
          buffer.append(expr.getText)
        case _ =>
          buffer.append(Ellipsis)
      }
    }
  }

  private val typeAnnotationRendererSimple: TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateNone)
  private val typeAnnotationRendererMinimized: TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateAllMinimized)
  private val typeAnnotationRendererFull: TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, parameterTypeDecoratorFull)

  private val functionParametersRenderer: ParametersRenderer = {
    val paramRenderer = new ParameterRenderer(typeRenderer, WithHtmlPsiLink.modifiersRenderer, typeAnnotationRendererSimple)
    new ParametersRenderer(paramRenderer, shouldRenderImplicitModifier = false)
  }

  @HintText
  def getQuickNavigateInfo(element: PsiElement): Option[String] = {
    val buffer = new StringBuilder
    element match {
      case scGiven: ScGiven                              => generateGivenInfo(buffer, scGiven)
      case clazz: ScTypeDefinition                       => generateClassInfo(buffer, clazz)
      case constructor: ScPrimaryConstructor             => generateClassInfo(buffer, constructor.containingClass)
      case function: ScFunction                          => generateFunctionInfo(buffer, function)
      case comp: ScNamedTupleComponent                   => generateNamedTupleComponentInfo(buffer, comp)
      case field@inNameContext(value: ScValueOrVariable) => generateValueInfo(buffer, field, value)
      case alias: ScTypeAlias                            => generateTypeAliasInfo(buffer, alias)
      case parameter: ScParameter                        => generateParameterInfo(buffer, parameter)
      case b: ScBindingPattern                           => generateBindingPatternInfo(buffer, b)
      case _                                             =>
    }

    val result = buffer.result().stripTrailing()
    // Do not show an empty pop up, let the platform show the fallback option
    if (result.isEmpty) Option.empty else Option(result)
  }

  private def generateClassInfo(buffer: StringBuilder, clazz: ScTypeDefinition): Unit = {
    renderClassHeader(buffer, clazz)
    modifiersRenderer.render(buffer, clazz)
    buffer.append(clazz.keywordPrefix)
    buffer.append(clazz.name)
    typeParamsRenderer.renderParams(buffer, clazz)
    buffer.append(constructor(clazz).map(_.parameterList).map(functionParametersRenderer.renderClauses).getOrElse(""))
    buffer.append(renderSuperTypes(clazz))
  }

  private def renderClassHeader(buffer: StringBuilder, clazz: ScTypeDefinition): Unit = {
    val module = ModuleUtilCore.findModuleForPsiElement(clazz)
    if (module != null)
      buffer.append(s"[${module.getName}] ")

    val locationString = clazz.getPresentation.getLocationString
    val length = locationString.length
    if (length > 1)
      buffer.append(locationString.substring(1, length - 1)) // remove brackets
    if (buffer.nonEmpty)
      buffer.append("\n")
  }

  private def constructor(clazz: ScTypeDefinition) =
    clazz match {
      case clazz: ScClass => clazz.constructor
      case _              => None
    }

  // TODO: for case classes Product is displayed but Serializable is not, UNIFY1
  private def renderSuperTypes(clazz: ScTypeDefinition)
                              : String = {
    val buffer = new StringBuilder()

    val superTypes = clazz.superTypes

    val isTooComplex = {
      //      val VisibleCharsThreshold = 200
      //      buffer.length > VisibleCharsThreshold
      false
    }
    val printEachSuperOnNewLine = isTooComplex

    superTypes match {
      case extendsType :: withTypes =>
        if (isJavaLangObject(extendsType) && clazz.is[ScTrait]) {
          // ignore
        } else {
          if (printEachSuperOnNewLine)
            buffer.append("\n")
          buffer.append(" extends ")
          buffer.append(typeRenderer.render(extendsType))
        }

        if (withTypes.nonEmpty && !printEachSuperOnNewLine)
          buffer.append("\n")

        // TODO: this is bad hack cause `with Object` can be displayed in many other places for trait, e.g. for method return type
        withTypes.iterator.filterNot(isJavaLangObject).foreach { superType =>
          if (printEachSuperOnNewLine)
            buffer.append("\n")
          buffer.append(" with ")
          buffer.append(typeRenderer.render(superType))
        }
      case _ =>
    }

    buffer.result()
  }

  private def isJavaLangObject(scType: ScType): Boolean =
    scType match {
      case ScDesignatorType(clazz: PsiClass) => clazz.isJavaLangObject
      case _                                 => false
    }

  private def generateFunctionInfo(buffer: StringBuilder, function: ScFunction): Unit = {
    renderMemberHeader(buffer, function)
    modifiersRenderer.render(buffer, function)
    // NOTE: currently it duplicates org.jetbrains.plugins.scala.lang.psi.types.api.presentation.FunctionRenderer
    // but it am not unifying those yet just to leave function/class/alias/val quick info generation code structure similar
    buffer.append("def ")
    buffer.append(function.name)
    typeParamsRenderer.renderParams(buffer, function)
    functionParametersRenderer.renderClauses(buffer, function.paramClauses.clauses)
    typeAnnotationRendererSimple.render(buffer, function)
  }

  private def generateGivenInfo(buffer: StringBuilder, scGiven: ScGiven): Unit = {
    renderMemberHeader(buffer, scGiven)
    modifiersRenderer.render(buffer, scGiven)
    buffer.append("given ")
    buffer.append(scGiven.name)
    typeAnnotationRendererSimple.render(buffer, scGiven)
  }

  private def renderMemberHeader(buffer: StringBuilder, member: ScMember): Unit =
    if (member.getParent.is[ScTemplateBody] && member.getParent.getParent.getParent.is[ScTypeDefinition]) {
      val clazz = member.containingClass
      if (clazz == null)
        return

      // TODO: should we remove [] from getLocationString (see renderClassHeader and unify)
      buffer
        .append(HtmlPsiUtils.psiElementLink(clazz.qualifiedName, clazz.name, addCodeTag = false))
        .append(" ")
        .append(clazz.getPresentation.getLocationString)
        .append("\n")
    }

  private def generateNamedTupleComponentInfo(buffer: StringBuilder, component: ScNamedTupleComponent): Unit = {
    component.getParent match {
      case typeable: Typeable =>
        typeAnnotationRendererMinimized.renderWithoutColon(buffer, typeable)
        buffer.append('.')
        buffer.append(component.name)
      case _ =>
    }
  }

  /**
   * TODO: improve SCL-17582
   *
   * @example member: `val (field1, field2) = (1, 2)`
   *          field: `field2`
   */
  private def generateValueInfo(buffer: StringBuilder, field: PsiNamedElement, member: ScValueOrVariable): Unit = {
    renderMemberHeader(buffer, member)
    modifiersRenderer.render(buffer, member)
    buffer.append(member.keyword).append(" ")
    buffer.append(field.name)

    field match {
      case typed: ScTypedDefinition =>
        typeAnnotationRendererMinimized.render(buffer, typed)
      case _ =>
    }

    val maybeExpr = member.definitionExpr
    maybeExpr
      .flatMap { expr =>
        val path = field.withParents
          .takeWhile(_ != member)
          .toList.reverse
          .dropWhile(e => !e.is[ScPattern]) // we want to start with the highest pattern

        if (path.nonEmpty) findCorrespondingExpr(path, expr)
        else if (member.declaredElements.contains(field)) maybeExpr
        else None
      }
      .foreach { expr =>
        buffer.append(" = ")
        buffer.append(getOneLine(expr.getText))
      }
  }

  @tailrec
  private def findCorrespondingExpr(path: List[PsiElement], expr: ScExpression): Option[ScExpression] = (path, expr) match {
    // ignore elements that are not really destructed
    case ((_: ScParenthesisedPattern | _: ScNamingPattern) :: rest, expr) => findCorrespondingExpr(rest, expr)
    case (path, ScParenthesisedExpr(expr)) => findCorrespondingExpr(path, expr)

    // stop when we reached the end of the path
    case (Nil, expr) => Some(expr)
    case (_ :: Nil, expr) => Some(expr)

    // deconstruct element
    case (ScTuplePattern(_) :: ScPatterns(patterns) :: (rest@(child :: _)), ScTuple(exprs)) if patterns.size == exprs.size =>
      exprs.lift(patterns.indexOf(child)) match {
        case Some(expr) => findCorrespondingExpr(rest, expr)
        case None => None
      }
    case (ScNamedTuplePattern(patternComps) :: (comp: ScNamedTuplePatternComponent) :: rest, ScNamedTuple(exprsComps)) if patternComps.size == exprsComps.size =>
      exprsComps.lift(patternComps.indexOf(comp)).flatMap(_.expr) match {
        case Some(expr) => findCorrespondingExpr(rest, expr)
        case None => None
      }
    //  TODO: more cases... like constructor-pattern+case-class
    case _ =>
      None
  }

  private def getOneLine(s: String): String = {
    val trimed = s.trim
    val newLineIdx = trimed.indexOf('\n')
    if (newLineIdx == -1) trimed
    else trimed.substring(0, newLineIdx) + " ..."
  }

  private def generateBindingPatternInfo(buffer: StringBuilder, binding: ScBindingPattern): Unit = {
    buffer.append("Pattern: ")
    buffer.append(binding.name)
    typeAnnotationRendererMinimized.render(buffer, binding)
  }

  private def generateTypeAliasInfo(buffer: StringBuilder, alias: ScTypeAlias): Unit = {
    renderMemberHeader(buffer, alias)
    buffer.append("type ")
    buffer.append(alias.name)
    typeParamsRenderer.renderParams(buffer, alias)

    alias match {
      case d: ScTypeAliasDefinition =>
        buffer.append(" = ")
        buffer.append(typeRenderer.render(d.aliasedType.getOrAny))
      case _ =>
    }
  }

  private def generateParameterInfo(buffer: StringBuilder, parameter: ScParameter): Unit =
    ScalaPsiUtil.findSyntheticContextBoundInfo(parameter) match {
      case Some(ContextBoundInfo(typeParam, contextBoundType, _, _)) =>
        // this branch can be triggered when showing, test on this example (expand all implicit hints):
        // trait Show[T]
        //  def foo[T : Show](x: T): String = implicitly
        // NOTE: link is not added because the tooltip content shown on a synthetic
        // parameter can't be hovered with mouse itself
        buffer.append("context bound ").append(typeParam.name).append(" : ").append(contextBoundType.getText)
      case _ =>
        generateSimpleParameterInfo(buffer, parameter)
    }

  private def generateSimpleParameterInfo(buffer: StringBuilder, parameter: ScParameter): Unit = {
    renderParameterHeader(buffer, parameter)
    if (parameter.isVal) buffer.appendKeyword("val ")
    else if (parameter.isVar) buffer.appendKeyword("var ")
    buffer.append(parameter.name)
    typeAnnotationRendererFull.render(buffer, parameter)
  }

  private def renderParameterHeader(buffer: StringBuilder, parameter: ScParameter): Unit =
    parameter match {
      case ConstructorParameter(clazz) =>
        buffer.append(clazz.name).append(" ").append(clazz.getPresentation.getLocationString).append("\n")
      case _                           =>
        // TODO: add some header with location info if it's function function parameter (currently none generated at all)
    }

  private object ConstructorParameter {
    def unapply(parameter: ScParameter): Option[ScTemplateDefinition] =
      parameter match {
        case clParameter: ScClassParameter => Option(clParameter.containingClass)
        case _                             => None
      }
  }

  private implicit class ScValueOrVariableOps(private val target: ScValueOrVariable) {
    def keyword: String  = target match {
      case _: ScValue => "val"
      case _          => "var"
    }
    def definitionExpr: Option[ScExpression] = target match {
      case d: ScPatternDefinition  => d.expr
      case d: ScVariableDefinition => d.expr
      case _                       => None
    }
  }
}
