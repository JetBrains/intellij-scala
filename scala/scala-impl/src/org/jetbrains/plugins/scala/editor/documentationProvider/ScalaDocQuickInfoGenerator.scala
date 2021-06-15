package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{ContextBoundInfo, inNameContext}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorateOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.{HtmlPsiUtils, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable

// TODO 1: analyze performance and whether rendered info is cached?
// TODO 2:  (!) quick info on the element itself should lead to "Show find usages" tooltip, no to quick info tooltip
//  (unify with Java behaviour)
// TODO 3: add minimum required module/location, if class/method is in same scope, do not render module/location at all
object ScalaDocQuickInfoGenerator {

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
    implicit def typeRenderer: TypeRenderer = substitutor(_).urlText(originalElement)
    val text = element match {
      case clazz: ScTypeDefinition                       => generateClassInfo(clazz)
      case function: ScFunction                          => generateFunctionInfo(function)
      case field@inNameContext(value: ScValueOrVariable) => generateValueInfo(field, value)
      case alias: ScTypeAlias                            => generateTypeAliasInfo(alias)
      case parameter: ScParameter                        => generateParameterInfo(parameter)
      case b: ScBindingPattern                           => generateBindingPatternInfo(b)
      case _                                             => null
    }
    text
  }

  private def generateClassInfo(clazz: ScTypeDefinition)
                               (implicit typeRenderer: TypeRenderer): String = {
    val buffer = new StringBuilder

    buffer.append(renderClassHeader(clazz))
    buffer.append(modifiersRenderer.render(clazz))
    buffer.append(clazz.keywordPrefix)
    buffer.append(clazz.name)
    buffer.append(typeParamsRenderer.renderParams(clazz))
    buffer.append(constructor(clazz).map(_.parameterList).map(functionParametersRenderer.renderClauses).getOrElse(""))
    buffer.append(renderSuperTypes(clazz))

    buffer.result
  }

  private def renderClassHeader(clazz: ScTypeDefinition): String = {
    val buffer = new mutable.StringBuilder()

    val module = ModuleUtilCore.findModuleForPsiElement(clazz)
    if (module != null)
      buffer.append(s"[${module.getName}] ")

    val locationString = clazz.getPresentation.getLocationString
    val length = locationString.length
    if (length > 1)
      buffer.append(locationString.substring(1, length - 1)) // remove brackets
    if (buffer.nonEmpty)
      buffer.append("\n")

    buffer.result()
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
        if (isJavaLangObject(extendsType) && clazz.isInstanceOf[ScTrait]) {
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

    buffer.result
  }

  private def isJavaLangObject(scType: ScType): Boolean =
    scType match {
      case ScDesignatorType(clazz: PsiClass) => clazz.isJavaLangObject
      case _                                 => false
    }

  private def generateFunctionInfo(function: ScFunction)
                                  (implicit typeRenderer: TypeRenderer): String = {
    val buffer = new StringBuilder
    buffer.append(renderMemberHeader(function))
    buffer.append(modifiersRenderer.render(function))
    // NOTE: currently it duplicates org.jetbrains.plugins.scala.lang.psi.types.api.presentation.FunctionRenderer
    // but it am not unifying those yet just to leave function/class/alias/val quick info generation code structure similar
    buffer.append("def ")
    buffer.append(function.name)
    buffer.append(typeParamsRenderer.renderParams(function))
    buffer.append(functionParametersRenderer.renderClauses(function))
    buffer.append(simpleTypeAnnotationRenderer.render(function))
    buffer.result
  }

  private def renderMemberHeader(member: ScMember): String = {
    if (!member.getParent.isInstanceOf[ScTemplateBody]) return ""
    if (!member.getParent.getParent.getParent.isInstanceOf[ScTypeDefinition]) return ""
    val clazz = member.containingClass
    // TODO: should we remove [] from getLocationString (see renderClassHeader and unify)
    HtmlPsiUtils.classLink(clazz) + " " + clazz.getPresentation.getLocationString + "\n"
  }

  /**
   * TODO: improve SCL-17582
   *
   * @example member: `val (field1, field2) = (1, 2)`
   *          field: `field2`
   */
  private def generateValueInfo(field: PsiNamedElement, member: ScValueOrVariable)
                               (implicit typeRenderer: TypeRenderer): String = {
    val buffer = new StringBuilder
    buffer.append(renderMemberHeader(member))
    buffer.append(modifiersRenderer.render(member))
    buffer.append(member.keyword).append(" ")
    buffer.append(field.name)

    field match {
      case typed: ScTypedDefinition =>
        buffer.append(richTypeAnnotationRenderer.render(typed))
      case _ =>
    }
    member.definitionExpr match {
      case Some(definition) =>
        buffer.append(" = ")
        buffer.append(getOneLine(definition.getText))
      case _ =>
    }

    buffer.result
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

  private def generateBindingPatternInfo(binding: ScBindingPattern)
                                        (implicit typeRenderer: TypeRenderer): String = {
    val buffer = new StringBuilder
    buffer.append("Pattern: ")
    buffer.append(binding.name)
    buffer.append(richTypeAnnotationRenderer.render(binding))
    buffer.result
  }

  private def generateTypeAliasInfo(alias: ScTypeAlias)
                                   (implicit typeRenderer: TypeRenderer): String = {
    val buffer = new StringBuilder
    buffer.append(renderMemberHeader(alias))
    buffer.append("type ")
    buffer.append(alias.name)
    buffer.append(typeParamsRenderer.renderParams(alias))

    alias match {
      case d: ScTypeAliasDefinition =>
        buffer.append(" = ")
        buffer.append(typeRenderer.render(d.aliasedType.getOrAny))
      case _ =>
    }
    buffer.result
  }

  private def generateParameterInfo(parameter: ScParameter)
                                   (implicit typeRenderer: TypeRenderer): String =
    ScalaPsiUtil.findSyntheticContextBoundInfo(parameter) match {
      case Some(ContextBoundInfo(typeParam, contextBoundType, _)) =>
        // this branch can be triggered when showing, test on this example (expand all implicit hints):
        // trait Show[T]
        //  def foo[T : Show](x: T): String = implicitly
        // NOTE: link is not added because the tooltip content shown on a synthetic
        // parameter can't be hovered with mouse itself
        val boundText = contextBoundType.getText
        s"context bound ${typeParam.name} : $boundText"
      case _ =>
        generateSimpleParameterInfo(parameter)
    }

  private def generateSimpleParameterInfo(parameter: ScParameter)
                                         (implicit typeRenderer: TypeRenderer): String = {
    val header = renderParameterHeader(parameter)
    val keyword = if (parameter.isVal) "val " else if (parameter.isVar) "var " else ""
    val name = parameter.name
    val typeAnnotation = richTypeAnnotationRenderer.render(parameter)
    s"$header$keyword$name$typeAnnotation"
  }

  private def renderParameterHeader(parameter: ScParameter) =
    parameter match {
      case ConstructorParameter(clazz) =>
        val className = clazz.name
        val location  = clazz.getPresentation.getLocationString
        s"$className $location\n"
      case _                           =>
        "" // TODO: add some header with location info if it's function function parameter (currently none generated at all)
    }

  private object ConstructorParameter {
    def unapply(parameter: ScParameter): Option[ScTemplateDefinition] =
      parameter match {
        case clParameter: ScClassParameter => Option(clParameter.containingClass)
        case _                             => None
      }
  }

  private def modifiersRenderer: ModifiersRendererLike = modList => {
    val buffer = new StringBuilder()
    import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps
    modList.modifiers.foreach { m =>
      buffer.append(m.text).append(" ")
    }
    buffer.result
  }

  private def typeParamsRenderer(implicit typeRenderer: TypeRenderer): TypeParamsRenderer =
    new TypeParamsRenderer(typeRenderer, TextEscaper.Html, stripContextTypeArgs = false)

  private def functionParametersRenderer(implicit typeRenderer: TypeRenderer): ParametersRenderer = {
    val paramRenderer = new ParameterRenderer(typeRenderer, ModifiersRenderer.WithHtmlPsiLink, simpleTypeAnnotationRenderer)
    new ParametersRenderer(paramRenderer)
  }

  private def richTypeAnnotationRenderer(implicit typeRenderer: TypeRenderer): TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorateOptions.DecorateAll)

  private def simpleTypeAnnotationRenderer(implicit typeRenderer: TypeRenderer): TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorateOptions.DecorateNone)
}
