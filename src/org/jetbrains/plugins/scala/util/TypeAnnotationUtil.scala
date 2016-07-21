package org.jetbrains.plugins.scala.util

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.formatting.settings.{ScalaCodeStyleSettings, TypeAnnotationPolicy, TypeAnnotationRequirement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.stubs.ScMemberOrLocal

/**
  * Created by kate on 7/14/16.
  */
object TypeAnnotationUtil {
  def needTypeAnnotation(requiment: Int, ovPolicy: Int, simplePolicy: Int, isOverride: Boolean, isSimple: Boolean): Boolean = {

    requiment != TypeAnnotationRequirement.Optional.ordinal() &&
      (!isSimple || simplePolicy != TypeAnnotationPolicy.Optional.ordinal()) &&
      (!isOverride || ovPolicy != TypeAnnotationPolicy.Optional.ordinal())
  }

  def needTypeAnnotation(element: PsiElement): Boolean = {
    val settings = ScalaCodeStyleSettings.getInstance(element.getProject)

    element match {
      case value: ScPatternDefinition if value.isSimple => //not simple will contains more than one declaration

        needTypeAnnotation(requirementForProperty(value, settings),
          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
          value.declaredElements.headOption.map(ScalaPsiUtil.superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty),
          value.expr.exists(isSimple))

      case variable: ScVariableDefinition if variable.isSimple =>

        needTypeAnnotation(requirementForProperty(variable, settings),
          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
          variable.declaredElements.headOption.map(ScalaPsiUtil.superValsSignatures(_, withSelfType = true)).exists(_.nonEmpty),
          variable.expr.exists(isSimple))

      case method: ScFunctionDefinition if method.hasAssign && !method.isSecondaryConstructor =>

        needTypeAnnotation(requirementForProperty(method, settings),
          settings.OVERRIDING_METHOD_TYPE_ANNOTATION,
          settings.SIMPLE_METHOD_TYPE_ANNOTATION,
          method.superSignaturesIncludingSelfType.nonEmpty,
          method.body.exists(isSimple))

//for java only (used in java converter)
//      case jLocal: PsiLocalVariable =>
//        helper(
//          settings.LOCAL_PROPERTY_TYPE_ANNOTATION,
//          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
//          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
//          jLocal.hasModifierProperty("override"), // TODO: check if it is true
//          isSimple = true
//        )
//      case jField: PsiField =>
//
//        helper(
//          requirementForProperty(jField, settings),
//          settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
//          settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
//          jField.hasModifierProperty("override"),
//          isSimple = false
//        )
//
////todo handle primary constructor
//      case jMethod: PsiMethod if jMethod.getBody != null =>
//
//        helper(
//          requirementForMethod(jMethod, settings),
//          settings.OVERRIDING_METHOD_TYPE_ANNOTATION,
//          settings.SIMPLE_METHOD_TYPE_ANNOTATION,
//          jMethod.hasModifierProperty("override"),
//          isSimple = false
//        )

      case _ => true //add type otherwise
    }
  }

//  def requirementForProperty(element: PsiElement, settings: ScalaCodeStyleSettings): Int = {
//    val isLocal = element match {
//      case member: ScMember =>
//    }
//  }

  def requirementForProperty(isLocal: Boolean, isPrivate: Boolean, isPublic: Boolean, isProtected: Boolean, settings: ScalaCodeStyleSettings): Int = {
    if (isLocal) {
      settings.LOCAL_PROPERTY_TYPE_ANNOTATION
    } else {
      if (isPrivate) settings.PRIVATE_PROPERTY_TYPE_ANNOTATION
      else if (isProtected) settings.PROTECTED_PROPERTY_TYPE_ANNOTATION
      else settings.PUBLIC_PROPERTY_TYPE_ANNOTATION
    }
  }

  def requirementForMethod(isLocal: Boolean, isPrivate: Boolean, isPublic: Boolean, isProtected: Boolean, settings: ScalaCodeStyleSettings): Int = {
    if (isLocal) {
      settings.LOCAL_METHOD_TYPE_ANNOTATION
    } else {
      if (isPrivate) settings.PRIVATE_METHOD_TYPE_ANNOTATION
      else if (isProtected) settings.PROTECTED_METHOD_TYPE_ANNOTATION
      else settings.PUBLIC_METHOD_TYPE_ANNOTATION
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

  // TODO: isSimple for Java
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
    case _ if psiElement.getContext != null => !psiElement.getContext.isInstanceOf[ScTemplateBody]
    case _ => false
  }


}
