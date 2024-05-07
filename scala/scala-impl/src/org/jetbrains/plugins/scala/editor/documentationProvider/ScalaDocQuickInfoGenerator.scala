package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.editor.documentationProvider.renderers.{ScalaDocTypeRenderer, WithHtmlPsiLink}
import org.jetbrains.plugins.scala.extensions.{NonNullObjectExt, ObjectExt, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{ContextBoundInfo, inNameContext}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScTuple}
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
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

// TODO 1: analyze performance and whether rendered info is cached?
// TODO 2:  (!) quick info on the element itself should lead to "Show find usages" tooltip, no to quick info tooltip
//  (unify with Java behaviour)
// TODO 3: add minimum required module/location, if class/method is in same scope, do not render module/location at all
object ScalaDocQuickInfoGenerator {

  //TODO: not supported yet
  private[documentationProvider] val EnableSyntaxHighlightingInQuickInfo = false

  def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement): String = {
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

  def getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement, substitutor: ScSubstitutor): String = {
    implicit val typeRenderer: TypeRenderer = ScalaDocTypeRenderer.forQuickInfo(originalElement, substitutor)(ProjectContext.fromPsi(element))
    val buffer = new StringBuilder
    element match {
      case scGiven: ScGiven                              => generateGivenInfo(buffer, scGiven)
      case clazz: ScTypeDefinition                       => generateClassInfo(buffer, clazz)
      case constructor: ScPrimaryConstructor             => generateClassInfo(buffer, constructor.containingClass)
      case function: ScFunction                          => generateFunctionInfo(buffer, function)
      case field@inNameContext(value: ScValueOrVariable) => generateValueInfo(buffer, field, value)
      case alias: ScTypeAlias                            => generateTypeAliasInfo(buffer, alias)
      case parameter: ScParameter                        => generateParameterInfo(buffer, parameter)
      case b: ScBindingPattern                           => generateBindingPatternInfo(buffer, b)
      case _                                             =>
    }

    val result = buffer.result().stripTrailing()
    // Do not show an empty pop up, let the platform show the fallback option
    if (result.isEmpty) null else result
  }

  private def generateClassInfo(buffer: StringBuilder, clazz: ScTypeDefinition)
                               (implicit typeRenderer: TypeRenderer): Unit = {
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
                              (implicit typeRenderer: TypeRenderer): String = {
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

  private def generateFunctionInfo(buffer: StringBuilder, function: ScFunction)
                                  (implicit typeRenderer: TypeRenderer): Unit = {
    renderMemberHeader(buffer, function)
    modifiersRenderer.render(buffer, function)
    // NOTE: currently it duplicates org.jetbrains.plugins.scala.lang.psi.types.api.presentation.FunctionRenderer
    // but it am not unifying those yet just to leave function/class/alias/val quick info generation code structure similar
    buffer.append("def ")
    buffer.append(function.name)
    typeParamsRenderer.renderParams(buffer, function)
    functionParametersRenderer.renderClauses(buffer, function.paramClauses.clauses)
    simpleTypeAnnotationRenderer.render(buffer, function)
  }

  private def generateGivenInfo(buffer: StringBuilder, scGiven: ScGiven)
                               (implicit typeRenderer: TypeRenderer): Unit = {
    renderMemberHeader(buffer, scGiven)
    modifiersRenderer.render(buffer, scGiven)
    buffer.append("given ")
    buffer.append(scGiven.name)
    simpleTypeAnnotationRenderer.render(buffer, scGiven)
  }

  private def renderMemberHeader(buffer: StringBuilder, member: ScMember): Unit =
    if (member.getParent.is[ScTemplateBody] && member.getParent.getParent.getParent.is[ScTypeDefinition]) {
      val clazz = member.containingClass
      // TODO: should we remove [] from getLocationString (see renderClassHeader and unify)
      buffer.append(HtmlPsiUtils.classLinkWithLabel(clazz, clazz.name, addCodeTag = false, defLinkHighlight = !EnableSyntaxHighlightingInQuickInfo))
        .append(" ")
        .append(clazz.getPresentation.getLocationString)
        .append("\n")
    }

  /**
   * TODO: improve SCL-17582
   *
   * @example member: `val (field1, field2) = (1, 2)`
   *          field: `field2`
   */
  private def generateValueInfo(buffer: StringBuilder, field: PsiNamedElement, member: ScValueOrVariable)
                               (implicit typeRenderer: TypeRenderer): Unit = {
    renderMemberHeader(buffer, member)
    modifiersRenderer.render(buffer, member)
    buffer.append(member.keyword).append(" ")
    buffer.append(field.name)

    field match {
      case typed: ScTypedDefinition =>
        richTypeAnnotationRenderer.render(buffer, typed)
      case _ =>
    }
    member.definitionExpr match {
      case Some(ScTuple(exprs)) =>
        member
          .declaredElements
          .zip(exprs)
          .collectFirst { case (decl, expr) if decl.name == field.name => expr }
          .foreach { expr =>
            buffer.append(" = ")
            buffer.append(getOneLine(expr.getText))
          }
      case Some(definition) =>
        buffer.append(" = ")
        buffer.append(getOneLine(definition.getText))
      case _ =>
    }
  }

  private implicit class ScValueOrVariableOps(private val target: ScValueOrVariable) extends AnyVal {
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

  private def getOneLine(s: String): String = {
    val trimed = s.trim
    val newLineIdx = trimed.indexOf('\n')
    if (newLineIdx == -1) trimed
    else trimed.substring(0, newLineIdx) + " ..."
  }

  private def generateBindingPatternInfo(buffer: StringBuilder, binding: ScBindingPattern)
                                        (implicit typeRenderer: TypeRenderer): Unit = {
    buffer.append("Pattern: ")
    buffer.append(binding.name)
    richTypeAnnotationRenderer.render(buffer, binding)
  }

  private def generateTypeAliasInfo(buffer: StringBuilder, alias: ScTypeAlias)
                                   (implicit typeRenderer: TypeRenderer): Unit = {
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

  private def generateParameterInfo(buffer: StringBuilder, parameter: ScParameter)
                                   (implicit typeRenderer: TypeRenderer): Unit =
    ScalaPsiUtil.findSyntheticContextBoundInfo(parameter) match {
      case Some(ContextBoundInfo(typeParam, contextBoundType, _)) =>
        // this branch can be triggered when showing, test on this example (expand all implicit hints):
        // trait Show[T]
        //  def foo[T : Show](x: T): String = implicitly
        // NOTE: link is not added because the tooltip content shown on a synthetic
        // parameter can't be hovered with mouse itself
        buffer.append("context bound ").append(typeParam.name).append(" : ").append(contextBoundType.getText)
      case _ =>
        generateSimpleParameterInfo(buffer, parameter)
    }

  private def generateSimpleParameterInfo(buffer: StringBuilder, parameter: ScParameter)
                                         (implicit typeRenderer: TypeRenderer): Unit = {
    renderParameterHeader(buffer, parameter)
    if (parameter.isVal) buffer.appendKeyword("val ")
    else if (parameter.isVar) buffer.appendKeyword("var ")
    buffer.append(parameter.name)
    richTypeAnnotationRenderer.render(buffer, parameter)
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

  private def modifiersRenderer: ModifiersRendererLike = (buffer, modListOwner) => {
    import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps
    modListOwner.getModifierList.modifiers.foreach { m =>
      buffer.append(m.text).append(" ")
    }
  }

  private def typeParamsRenderer(implicit typeRenderer: TypeRenderer): TypeParamsRenderer =
    new TypeParamsRenderer(typeRenderer, TextEscaper.Html, stripContextTypeArgs = false)

  private def functionParametersRenderer(implicit typeRenderer: TypeRenderer): ParametersRenderer = {
    val paramRenderer = new ParameterRenderer(typeRenderer, WithHtmlPsiLink.modifiersRenderer, simpleTypeAnnotationRenderer)
    new ParametersRenderer(paramRenderer, shouldRenderImplicitModifier = false)
  }

  private def richTypeAnnotationRenderer(implicit typeRenderer: TypeRenderer): TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateAllMinimized)

  private def simpleTypeAnnotationRenderer(implicit typeRenderer: TypeRenderer): TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorator.DecorateNone)
}
