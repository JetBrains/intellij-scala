package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.{IProperty, PropertiesImplUtil, PropertiesReferenceManager}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.{&, BooleanExt, NullSafe, ObjectExt, Parent, PsiMethodExt, PsiParameterExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import java.text.MessageFormat
import java.util.Collections
import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsJava}

object ScalaI18nUtil {
  // com.intellij.codeInspection.i18n.NlsInfo.NLS_SAFE is package-private
  private val NLS_SAFE = "com.intellij.openapi.util.NlsSafe"

  trait AnnotationChecker {
    def checkPsiModifierListOwner(owner: PsiModifierListOwner): Boolean
    def checkPsiAnnotationOwner(owner: PsiAnnotationOwner): Boolean
  }

  private sealed abstract class PassedToAnnotatedChecker(protected val annotations: Seq[String]) extends AnnotationChecker {
    private val annotationsSet: java.util.Set[String] = annotations match {
      case Seq(one) => Collections.singleton(one)
      case multiple => ContainerUtil.newHashSet(multiple.asJava)
    }

    override def checkPsiModifierListOwner(owner: PsiModifierListOwner): Boolean = {
      // annotated with one of the given annotations
      AnnotationUtil.findAnnotation(owner, annotationsSet) != null ||
        // annotated with Annotation itself annotated with one of the given annotations
        checkAnnotationsOfAnnotations(owner.getAnnotations)
    }

    override def checkPsiAnnotationOwner(owner: PsiAnnotationOwner): Boolean = {
      annotations.exists(owner.hasAnnotation) || checkAnnotationsOfAnnotations(owner.getAnnotations)
    }

    private def checkAnnotationsOfAnnotations(annotations: Array[PsiAnnotation]): Boolean =
      annotations.iterator
        .flatMap(resolveAnnotation)
        .exists(AnnotationUtil.findAnnotation(_, annotationsSet) != null)

    private def resolveAnnotation(annotation: PsiAnnotation): Option[PsiModifierListOwner] = {
      def resolveInScala =
        annotation
          .asOptionOf[ScAnnotation]
          .flatMap(_.typeElement.`type`().toOption)
          .flatMap(_.extractClass)

      def resolveInJava =
        annotation
          .getNameReferenceElement
          .toOption
          .flatMap(_.resolve().asOptionOf[PsiModifierListOwner])

      resolveInJava.orElse(resolveInScala)
    }
  }

  private object PassedToNlsChecker extends PassedToAnnotatedChecker(Seq(AnnotationUtil.NLS))

  private object PassedToNlsOrNlsSafeChecker extends PassedToAnnotatedChecker(Seq(AnnotationUtil.NLS, NLS_SAFE))

  def isPassedToNls(element: PsiElement): Boolean =
    ScalaI18nUtil.isPassedToAnnotated(element, PassedToNlsChecker)

  def mustBePropertyKey(literal: ScLiteral,
                        @Nullable annotationAttributeValues: mutable.HashMap[String, PsiAnnotationMemberValue] = null): Boolean = {
    isPassedToAnnotated(literal, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues)
  }

  def isPassedToAnnotated(element: PsiElement,
                          annFqn: String,
                          @Nullable
                          annotationAttributeValues: mutable.HashMap[String, PsiAnnotationMemberValue] = null): Boolean =
    isPassedToAnnotated(element, new AnnotationChecker {
      override def checkPsiModifierListOwner(owner: PsiModifierListOwner): Boolean =
        addToAnnotationAttributeValues(AnnotationUtil.findAnnotation(owner, annFqn))

      override def checkPsiAnnotationOwner(owner: PsiAnnotationOwner): Boolean =
        addToAnnotationAttributeValues(owner.findAnnotation(annFqn))

      private def addToAnnotationAttributeValues(@Nullable annotation: PsiAnnotation): Boolean = {
        if (annotation == null) {
          false
        } else {
          if (annotationAttributeValues != null) {
            val parameterList = annotation.getParameterList
            val attributes = parameterList.getAttributes
            for (attribute <- attributes) {
              val name: String = attribute.getName
              if (annotationAttributeValues.contains(name)) {
                annotationAttributeValues.put(name, attribute.getValue)
              }
            }
          }
          true
        }
      }
    })

  @tailrec
  def isPassedToAnnotated(@NotNull element: PsiElement, checker: AnnotationChecker): Boolean = {
    def isAnnotated(member: PsiElement): Boolean = ScalaI18nUtil.isAnnotated(member, checker)
    def checkParam(matchedParameters: Seq[(ScExpression, Parameter)]): Boolean =
      matchedParameters
        .find(_._1 == element)
        .flatMap { case (_, param) => param.psiParam }
        .exists(isAnnotated)

    element.getParent match {
      case argList: ScArgumentExprList =>
        checkParam(argList.matchedParameters)
      case infix@ScInfixExpr(_, _, `element`) =>
        checkParam(infix.matchedParameters)
      case (tuple: ScTuple) & Parent(infix@ScInfixExpr(_, _, arg)) if tuple == arg =>
        checkParam(infix.matchedParameters)
      case assign: ScAssignment =>
        assign
          .resolveAssignment
          .orElse(assign.leftExpression.asOptionOf[ScReferenceExpression].flatMap(_.bind()))
          .exists {
            case ScalaResolveResult(f: ScFunction, _) =>
              f.parameters.headOption.exists(isAnnotated)
            case ScalaResolveResult(e, _) =>
              isAnnotated(e)
            case _ =>
              false
          }
      case block: ScBlock if block.lastStatement.contains(element) =>
        isPassedToAnnotated(block, checker)
      case ScReturn.of(method) => isAnnotated(method)
      case f: ScFunctionDefinition => isAnnotated(f)
      case v: ScValueOrVariable => isAnnotated(v)
      case parenthesised: ScParenthesisedExpr =>
        isPassedToAnnotated(parenthesised, checker)
      case typed: ScTypedExpression if !typed.isSequenceArg =>
        isPassedToAnnotated(typed, checker)
      case matchCase: ScCaseClause =>
        // in match:      caseClause -> caseClauses -> matchExpr (in some other expression)
        // in call block: caseClause -> caseClauses -> block of expression (in argumentList)
        isPassedToAnnotated(matchCase.getParent.getParent, checker)
      case ifExpr: ScIf if !ifExpr.condition.contains(element) =>
        isPassedToAnnotated(ifExpr, checker)
      case _ => false
    }
  }

  def isMethodParameterAnnotated(method: PsiMethod, idx: Int, checker: AnnotationChecker): Boolean = {
    val params = method.parameters
    def varArgsParam = params.lastOption.filter(_.isVarArgs)
    val param: PsiParameter =
      params
        .lift(idx)
        .orElse(varArgsParam)
        .getOrElse(return false)
    isAnnotated(param, checker)
  }

  def isAnnotatedWithNlsOrNlsSafe(element: PsiElement): Boolean = isAnnotated(element, PassedToNlsOrNlsSafeChecker)

  def isAnnotated(element: PsiElement, checker: AnnotationChecker): Boolean = {
    import ScalaPsiUtil._
    def isDirectAnnotated(element: PsiElement): Boolean =
      checkAnnotationsOnElement(element, checker)
    def isSuperAnnotatedWith(element: PsiNamedElement): Boolean =
      superValsSignatures(element, withSelfType = true).iterator.map(_.namedElement).exists(isDirectAnnotated)
    element match {
      case param: ScClassParameter =>
        isDirectAnnotated(param) || isSuperAnnotatedWith(param)

      case v: ScValueOrVariable =>
        isDirectAnnotated(v) || v.declaredElements.exists(isSuperAnnotatedWith)

      case bindingPattern: ScBindingPattern =>
        isDirectAnnotated(bindingPattern) || isSuperAnnotatedWith(bindingPattern)

      case function: ScFunction =>
        isDirectAnnotated(function) || function.superSignaturesIncludingSelfType.map(_.namedElement).exists(isDirectAnnotated)

      case method: PsiMethod =>
        isDirectAnnotated(method) || method.findSuperMethods().exists(isDirectAnnotated)

      case field: PsiField =>
        isDirectAnnotated(field)

      case param: PsiParameter =>
        isDirectAnnotated(param) || {
          param.getDeclarationScope match {
            case apply: ScFunction if apply.isSynthetic && apply.isApplyMethod =>
              originalCaseClassParameter(apply, param.index)
                .exists(isAnnotated(_, checker))
            case method: PsiMethod =>
              val pIndex = param.index
              method
                .findSuperMethods()
                .iterator
                .flatMap(_.parameters.lift(pIndex))
                .exists(isDirectAnnotated)
            case _ =>
              // support more then methods
              false
          }
        }
      case _ =>
        false
    }
  }

  private def checkAnnotationsOnElement(element: PsiElement, checker: AnnotationChecker): Boolean = {
    def elementIsAnnotated = element match {
      case owner: PsiModifierListOwner => checker.checkPsiModifierListOwner(owner)
      case _ => false
    }
    def typeIsAnnotated: Boolean = {
      val ty = element match {
        case method: PsiMethod => NullSafe(method.getReturnType)
        case field: PsiField => NullSafe(field.getType)
        case _ => return false
      }
      ty.exists(checker.checkPsiAnnotationOwner)
    }

    elementIsAnnotated || typeIsAnnotated
  }

  def propertiesFilesByBundleName(bundleName: String, context: PsiElement): Seq[PropertiesFile] = {
    val propManager = PropertiesReferenceManager.getInstance(context.getProject)
    val module = ModuleUtilCore.findModuleForPsiElement(context)
    if (module == null) {
      Seq.empty
    } else {
      propManager.findPropertiesFiles(module, bundleName).asScala.toSeq
    }
  }

  def paramCountOf(property: IProperty): Option[Int] = {
    Option(property.getValue).flatMap { value =>
      try {
        val format = new MessageFormat(value)
        Some(format.getFormatsByArgumentIndex.length)
      } catch {
        case _: IllegalArgumentException => None
      }
    }
  }

  def resolveBundleName(bundleNameElement: PsiElement): Option[String] = {
    val result = JavaPsiFacade.getInstance(bundleNameElement.getProject).getConstantEvaluationHelper
      .computeConstantExpression(bundleNameElement)
    Option(result).map(_.toString)
  }

  case class PropertyReferenceResolver(expression: ScStringLiteral) {
    val key: String = expression.getValue

    lazy val bundleName: Option[String] = {
      val annotationAttributeValues = new mutable.HashMap[String, PsiAnnotationMemberValue]
      annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
      if (mustBePropertyKey(expression, annotationAttributeValues)) {
        annotationAttributeValues.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER).flatMap {
          case bundleName: PsiElement => resolveBundleName(bundleName)
          case _ => None
        }
      } else None
    }

    lazy val isPassedToPropertyKey: Boolean = bundleName.isDefined

    lazy val referencedPropertiesFiles: Seq[PropertiesFile] =
      bundleName match {
        case Some(name) => propertiesFilesByBundleName(name, expression)
        case None => Seq.empty
      }

    lazy val referencedProperties: Seq[IProperty] =
      referencedPropertiesFiles.flatMap(_.findPropertyByKey(key).toOption)

    lazy val referenceIsValid: Boolean =
      referencedProperties.nonEmpty

    lazy val possibleParamCounts: Seq[Int] =
      referencedProperties.flatMap(paramCountOf)
  }
}
