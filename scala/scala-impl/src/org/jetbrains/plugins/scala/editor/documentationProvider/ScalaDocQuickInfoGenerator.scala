package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ElementText, ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScModifierList, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScTypeAlias, ScTypeAliasDefinition, ScValue, ScVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypePresentationUtils}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil

// TODO 1: analyze whether rendered info is cached?
// TODO 2:  (!) quick info on the element itself should lead to "Show find usages" tooltip, no to quick info tooltip
//  (unify with Java behaviour)
// TODO 3: some methods use functional style, returning string, some use imperative, passing builders
//  unify those methods to use one style (probably using builder to improve performance (quick info is called frequently on mouse hover))
// TODO 4: add minimum required module/location, if class/method is in same scope, do not render module/location at all
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
    getQuickNavigateInfo(element, substitutor)
  }

  def getQuickNavigateInfo(element: PsiElement, substitutor: ScSubstitutor): String = {
    implicit val s: ScSubstitutor = substitutor
    val text = element match {
      case clazz: ScTypeDefinition                         => generateClassInfo(clazz)
      case function: ScFunction                            => generateFunctionInfo(function)
      case value@inNameContext(_: ScValue | _: ScVariable) => generateValueInfo(value)
      case alias: ScTypeAlias                              => generateTypeAliasInfo(alias)
      case parameter: ScParameter                          => generateParameterInfo(parameter)
      case b: ScBindingPattern                             => generateBindingPatternInfo(b)
      case _                                               => null
    }
    text
  }

  private def generateClassInfo(clazz: ScTypeDefinition)
                               (implicit subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    val module = ModuleUtilCore.findModuleForPsiElement(clazz)
    if (module != null)
      buffer.append('[').append(module.getName).append("] ")

    val locationString = clazz.getPresentation.getLocationString
    val length = locationString.length
    if (length > 1)
      buffer.append(locationString.substring(1, length - 1)) // remove brackets
    if (buffer.nonEmpty)
      buffer.append("\n")
    renderModifiersPresentableText(buffer, clazz.getModifierList)
    buffer.append(ScalaDocumentationUtils.getKeyword(clazz))
    clazz.`type`() match {
      case Right(typ) =>
        buffer.append(renderType(typ))
      case Left(_) =>
        // TODO: is this case possible? check when indicies are unavailable
        buffer.append(clazz.name)
        renderTypeParams(buffer, clazz)
    }

    renderConstructorText(buffer, clazz)
    renderSuperTypes(buffer, clazz)
    buffer.toString()
  }

  private def renderConstructorText(buffer: StringBuilder, clazz: ScTypeDefinition)
                                   (implicit subst: ScSubstitutor): Unit =
    clazz match {
      case clazz: ScClass =>
        clazz.constructor match {
          case Some(primaryConstructor) =>
            StructureViewUtil.renderParametersAsString(primaryConstructor.parameterList, short = false, subst)(buffer)
          case _ =>
        }
      case _ =>
    }

  private def renderSuperTypes(buffer: StringBuilder, clazz: ScTypeDefinition)
                              (implicit subst: ScSubstitutor): Unit = {
    val printEachOnNewLine = false // TODO: temp variable, extract to settings?
    val superTypes = clazz.superTypes
    superTypes match {
      case head :: tail =>
        if (isJavaLangObject(head) && clazz.isInstanceOf[ScTrait]) {
          // ignore
        } else {
          buffer.append(" extends ")
          buffer.append(renderType(head))
        }

        if (tail.nonEmpty && !printEachOnNewLine)
          buffer.append("\n")

        tail.iterator.filterNot(isJavaLangObject).foreach { superType =>
          if (printEachOnNewLine)
            buffer.append("\n")
          buffer.append(" with ")
          buffer.append(renderType(superType))
        }
      case _ =>
    }
  }

  // TODO 1: cover usages with tests
  // TODO 2: custom `trait Object` should be navigatable (now it navigates to `java.lang.Object`)
  private def isJavaLangObject(scType: ScType): Boolean =
    scType match {
      case designator: ScDesignatorType =>
        designator.element match {
          case clazz: PsiClass => clazz.qualifiedName == "java.lang.Object"
          case _               => false
        }
      case _ => false
    }

  private def renderType(typ: ScType)
                        (implicit subst: ScSubstitutor): String =
    subst(typ).urlText

  private def renderTypeParams(buffer: StringBuilder, paramsOwner: ScTypeParametersOwner)
                              (implicit subst: ScSubstitutor): Unit = {
    val parameters = paramsOwner.typeParameters
    if (parameters.nonEmpty) {
      buffer.append("[")
      var isFirst = true
      parameters.foreach { p =>
        if (isFirst)
          isFirst = false
        else
          buffer.append(", ")
        val paramRendered = renderTypeParam(p)
        buffer.append(paramRendered)
      }
      buffer.append("]")
    }
  }

  private def renderTypeParam(buffer: StringBuilder, param: ScTypeParam)
                             (implicit subst: ScSubstitutor): Unit = {
    buffer.append(param.name)
    renderTypeParamBounds(buffer, param)
  }

  private def renderTypeParamBounds(buffer: StringBuilder, param: ScTypeParam)
                                   (implicit subst: ScSubstitutor): Unit = {
    def append(boundElement: ScTypeElement, boundType: IElementType): Unit = {
      if (buffer.nonEmpty) buffer.append(" ")
      val boundTypeEscaped = boundType.toString.replace("<", "&lt;")
      val boundElementRendered = renderTypeElement(boundElement)
      buffer.append(boundTypeEscaped).append(" ").append(boundElementRendered)
    }
    val bounds = param.bounds
    bounds.foreach { case (bound, boundType) => append(bound, boundType) }
  }

  private def renderTypeElement(boundElement: ScTypeElement)
                               (implicit subst: ScSubstitutor): String =
    boundElement.`type`() match {
      case Right(typ) =>
        renderType(typ)
      case Left(_)  =>
        boundElement.getText // TODO: is this case possible?
    }

  private def generateFunctionInfo(function: ScFunction)
                                  (implicit subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(function))
    val list = function.getModifierList
    if (list != null)
      renderModifiersPresentableText(buffer, list)
    buffer.append("def ")
    buffer.append(ScalaPsiUtil.getMethodPresentableText(function, subst))
    buffer.toString()
  }

  private def getMemberHeader(member: ScMember): String = {
    if (!member.getParent.isInstanceOf[ScTemplateBody]) return ""
    if (!member.getParent.getParent.getParent.isInstanceOf[ScTypeDefinition]) return ""
    member.containingClass.name + " " + member.containingClass.getPresentation.getLocationString + "\n"
  }

  private def generateValueInfo(field: PsiNamedElement)
                               (implicit subst: ScSubstitutor): String = {
    val member = ScalaPsiUtil.nameContext(field) match {
      case x: ScMember => x
      case _ => return null
    }
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(member))
    renderModifiersPresentableText(buffer, member.getModifierList)
    member match {
      case value: ScValue =>
        buffer.append("val ")
        buffer.append(field.name)
        field match {
          case typed: ScTypedDefinition =>
            val typez = subst(typed.`type`().getOrAny)
            if (typez != null) buffer.append(": " + typez.presentableText(field))
          case _ =>
        }
        value match {
          case d: ScPatternDefinition =>
            buffer.append(" = ")
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
          case _ =>
        }
      case variable: ScVariable =>
        buffer.append("var ")
        buffer.append(field.name)
        field match {
          case typed: ScTypedDefinition =>
            val typez = subst(typed.`type`().getOrAny)
            if (typez != null) buffer.append(": " + typez.presentableText(field))
          case _ =>
        }
        variable match {
          case d: ScVariableDefinition =>
            buffer.append(" = ")
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
          case _ =>
        }
    }
    buffer.toString()
  }

  private def getOneLine(s: String): String = {
    val trimed = s.trim
    val i = trimed.indexOf('\n')
    if (i == -1) trimed else trimed.substring(0, i) + " ..."
  }

  private def generateBindingPatternInfo(binding: ScBindingPattern)
                                        (implicit subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append("Pattern: ")
    buffer.append(binding.name)
    val typez = subst(subst(binding.`type`().getOrAny))
    if (typez != null) buffer.append(": " + typez.presentableText(binding))

    buffer.toString()
  }

  private def generateTypeAliasInfo(alias: ScTypeAlias)
                                   (implicit subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(alias))
    buffer.append("type ")
    buffer.append(alias.name)
    renderTypeParams(buffer, alias)

    alias match {
      case d: ScTypeAliasDefinition =>
        buffer.append(" = ")
        val ttype = subst(d.aliasedType.getOrAny)
        buffer.append(ttype.presentableText(alias))
      case _ =>
    }
    buffer.toString()
  }

  private def generateParameterInfo(parameter: ScParameter)(implicit subst: ScSubstitutor): String =
    ScalaPsiUtil.withOriginalContextBound(parameter)(simpleParameterInfo(parameter)) {
      case (typeParam, ElementText(boundText), _) =>
        val clause = typeParam.typeParametersClause.fold("")(_.getText)
        s"context bound ${typeParam.name}$clause : $boundText"
    }

  private def simpleParameterInfo(parameter: ScParameter)
                                 (implicit subst: ScSubstitutor): String = {
    val name = parameter.name
    val typeAnnotation = ScalaTypePresentationUtils.typeAnnotationText(parameter)(subst.andThen(_.presentableText(parameter)))

    val defaultText = s"$name$typeAnnotation"

    val prefix = parameter match {
      case clParameter: ScClassParameter =>
        clParameter.containingClass.toOption.map { clazz =>
          val classWithLocation = clazz.name + " " + clazz.getPresentation.getLocationString + "\n"
          val keyword = if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else ""

          classWithLocation + keyword

        }.getOrElse("")
      case _ => ""
    }
    prefix + defaultText
  }

  private def renderModifiersPresentableText(buffer: StringBuilder, modList: ScModifierList): Unit = {
    import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps
    modList.modifiers.foreach { m =>
      buffer.append(m.text).append(" ")
    }
  }
}
