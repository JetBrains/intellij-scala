package org.jetbrains.plugins.scala.util

import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, TypeAnnotationPolicy, TypeAnnotationRequirement}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
  * Created by kate on 7/14/16.
  */
object TypeAnnotationUtil {
  def addTypeAnnotation(requiment: Int, ovPolicy: Int, simplePolicy: Int, isOverride: Boolean, isSimple: Boolean): Boolean = {

    requiment != TypeAnnotationRequirement.Optional.ordinal() &&
      (!isSimple || simplePolicy != TypeAnnotationPolicy.Optional.ordinal()) &&
      (!isOverride || ovPolicy != TypeAnnotationPolicy.Optional.ordinal())
  }

  def addTypeAnnotation(element: ScalaPsiElement): Boolean = {
    val settings = ScalaCodeStyleSettings.getInstance(element.getProject)

    element match {
      case value: ScPatternDefinition if value.isSimple => //not simple will contains more than one declaration

        addTypeAnnotation(requirementForProperty(value, settings),
          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
          isOverriding(value),
          value.expr.exists(isSimple))

      case variable: ScVariableDefinition if variable.isSimple =>

        addTypeAnnotation(requirementForProperty(variable, settings),
          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
          isOverriding(variable),
          variable.expr.exists(isSimple))

      case method: ScFunctionDefinition if method.hasAssign && !method.isSecondaryConstructor =>

        addTypeAnnotation(requirementForMethod(method, settings),
          settings.OVERRIDING_METHOD_TYPE_ANNOTATION,
          settings.SIMPLE_METHOD_TYPE_ANNOTATION,
          isOverriding(method),
          method.body.exists(isSimple))

      case _ => true
    }
  }

  def isOverriding(element: PsiElement): Boolean = {
    element match {
      case func: ScFunctionDefinition =>
        func.superSignaturesIncludingSelfType.nonEmpty
      case variable: ScVariableDefinition =>
        variable.declaredElements.headOption.map(ScalaPsiUtil.superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty)
      case pattern: ScPatternDefinition =>
        pattern.declaredElements.headOption.map(ScalaPsiUtil.superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty)
      case _ => false
    }
  }

  def requirementForProperty(isLocal: Boolean, visibility: Visibility, settings: ScalaCodeStyleSettings): Int = {
    if (isLocal)
      settings.LOCAL_PROPERTY_TYPE_ANNOTATION
    else visibility match {
      case Private => settings.PRIVATE_PROPERTY_TYPE_ANNOTATION
      case Protected => settings.PROTECTED_PROPERTY_TYPE_ANNOTATION
      case Public => settings.PUBLIC_PROPERTY_TYPE_ANNOTATION
    }
  }

  def requirementForMethod(isLocal: Boolean, visibility: Visibility, settings: ScalaCodeStyleSettings): Int = {
    if (isLocal)
      settings.LOCAL_METHOD_TYPE_ANNOTATION
    else visibility match {
      case Private => settings.PRIVATE_METHOD_TYPE_ANNOTATION
      case Protected => settings.PROTECTED_METHOD_TYPE_ANNOTATION
      case Public => settings.PUBLIC_METHOD_TYPE_ANNOTATION
    }
  }

  def requirementForProperty(property: ScMember, settings: ScalaCodeStyleSettings): Int = {
    if (property.isLocal) {
      settings.LOCAL_PROPERTY_TYPE_ANNOTATION
    } else {
      if (property.isPrivate) settings.PRIVATE_PROPERTY_TYPE_ANNOTATION
      else if (property.isProtected) settings.PROTECTED_PROPERTY_TYPE_ANNOTATION
      else settings.PUBLIC_PROPERTY_TYPE_ANNOTATION
    }
  }

  def requirementForMethod(method: ScFunctionDefinition, settings: ScalaCodeStyleSettings): Int = {
    if (method.isLocal) {
      settings.LOCAL_METHOD_TYPE_ANNOTATION
    } else {
      if (method.isPrivate) settings.PRIVATE_METHOD_TYPE_ANNOTATION
      else if (method.isProtected) settings.PROTECTED_METHOD_TYPE_ANNOTATION
      else settings.PUBLIC_METHOD_TYPE_ANNOTATION
    }
  }

  def isSimple(exp: ScExpression): Boolean = {
    exp match {
      case _: ScLiteral => true
      case _: ScNewTemplateDefinition => true
      case ref: ScReferenceExpression if ref.refName == "???" => true
      case ref: ScReferenceExpression if ref.refName(0).isUpper => true //heuristic for objects
      case call: ScMethodCall => call.getInvokedExpr match {
        case ref: ScReferenceExpression if ref.refName(0).isUpper => true //heuristic for case classes
        case _ => false
      }
      case _: ScThrowStmt => true
      case _ => false
    }
  }

  def isLocal(psiElement: PsiElement) = psiElement match {
    case member: ScMember => member.isLocal
    case _: PsiLocalVariable => true
    case _ if psiElement.getContext != null => !psiElement.getContext.isInstanceOf[ScTemplateBody]
    case _ => false
  }

  def getTypeElement(element: ScalaPsiElement): Option[ScTypeElement] = {
    element match {
      case fun: ScFunction => fun.returnTypeElement
      case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) => pd.typeElement
      case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) => vd.typeElement
      case _ => None
    }
  }

  def removeTypeAnnotationIfNeed(element: ScalaPsiElement): Unit = {
    getTypeElement(element) match {
      case Some(typeElement) if !addTypeAnnotation(element) =>
        AddOnlyStrategy.withoutEditor.removeTypeAnnotation(typeElement)
      case _ =>
    }
  }

  sealed abstract class Visibility

  case object Private extends Visibility

  case object Protected extends Visibility

  case object Public extends Visibility

  def visibilityFromString(visibilityString: String): Visibility = {
    if (visibilityString.contains("private"))
      TypeAnnotationUtil.Private
    else if (visibilityString.contains("protected"))
      TypeAnnotationUtil.Protected
    else TypeAnnotationUtil.Public
  }

}
