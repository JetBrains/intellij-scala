package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n

import java.text.MessageFormat
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.{IProperty, PropertiesImplUtil, PropertiesReferenceManager}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.{PsiMethodExt, _}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.mutable

object ScalaI18nUtil {
  trait AnnotationChecker {
    def check(owner: PsiModifierListOwner): Boolean
  }

  object PassedToNlsChecker extends AnnotationChecker {
    override def check(owner: PsiModifierListOwner): Boolean = {
      def resolveAnnotation(annotation: PsiAnnotation): Option[PsiModifierListOwner] = {
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


      // annotated with @Nls
      AnnotationUtil.findAnnotation(owner, AnnotationUtil.NLS) != null ||
        // annotated with Annotation itself annotated with @Nls
        owner
          .getAnnotations.iterator
          .flatMap(resolveAnnotation)
          .exists(AnnotationUtil.findAnnotation(_, AnnotationUtil.NLS) != null)
    }
  }

  def isPassedToNls(element: PsiElement): Boolean =
    ScalaI18nUtil.isPassedToAnnotated(element, PassedToNlsChecker)


  def mustBePropertyKey(literal: ScLiteral,
                        @Nullable annotationAttributeValues: mutable.HashMap[String, AnyRef] = null): Boolean = {
    isPassedToAnnotated(literal, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues)
  }

  def isPassedToAnnotated(element: PsiElement,
                          annFqn: String,
                          @Nullable
                          annotationAttributeValues: mutable.HashMap[String, AnyRef] = null): Boolean =
    isPassedToAnnotated(element, new AnnotationChecker {
      override def check(owner: PsiModifierListOwner): Boolean = {
        val annotation = AnnotationUtil.findAnnotation(owner, annFqn)
        if (annotation != null) {
          addToAnnotationAttributeValues(annotation)
          true
        } else false
      }

      private def addToAnnotationAttributeValues(annotation: PsiAnnotation): Unit = {
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
      case (tuple: ScTuple) && Parent(infix@ScInfixExpr(_, _, arg)) if tuple == arg =>
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

  def isAnnotatedWithNls(element: PsiElement): Boolean = isAnnotated(element, PassedToNlsChecker)

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
    element match {
      case owner: PsiModifierListOwner => checker.check(owner)
      case _ => false
    }
  }

  def isPropertyRef(expression: ScLiteral, key: String, resourceBundleName: String): Boolean = {
    if (resourceBundleName == null) {
      !PropertiesImplUtil.findPropertiesByKey(expression.getProject, key).isEmpty
    }
    else {
      val propertiesFiles = propertiesFilesByBundleName(resourceBundleName, expression)
      var containedInPropertiesFile: Boolean = false
      propertiesFiles.forEach { propertiesFile =>
        containedInPropertiesFile |= propertiesFile.findPropertyByKey(key) != null
      }
      containedInPropertiesFile
    }
  }

  def propertiesFilesByBundleName(resourceBundleName: String, context: PsiElement): java.util.List[PropertiesFile] = {
    var containingFile: PsiFile = context.getContainingFile
    val containingFileContext: PsiElement = containingFile.getContext
    if (containingFileContext != null) containingFile = containingFileContext.getContainingFile
    var virtualFile: VirtualFile = containingFile.getVirtualFile
    if (virtualFile == null) {
      virtualFile = containingFile.getOriginalFile.getVirtualFile
    }
    if (virtualFile != null) {
      val project: Project = containingFile.getProject
      val module: Module = ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile)
      if (module != null) {
        val refManager: PropertiesReferenceManager = PropertiesReferenceManager.getInstance(project)
        return refManager.findPropertiesFiles(module, resourceBundleName)
      }
    }
    java.util.Collections.emptyList()
  }

  /**
   * Returns number of different parameters in i18n message. For example, for string
   * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i>
   * number of parameters is 3.
   *
   * @param expression i18n literal
   * @return number of parameters
   */
  def getPropertyValueParamsMaxCount(expression: ScLiteral): Int = {
    var maxCount: Int = -1
    for (reference <- expression.getReferences) {
      reference match {
        case polyVarRef: PsiPolyVariantReference =>
          for (result <- polyVarRef.multiResolve(false)) {

            if (result.isValidResult && result.getElement.isInstanceOf[IProperty]) {
              val value: String = result.getElement.asInstanceOf[IProperty].getValue
              var format: MessageFormat = null
              try {
                format = new MessageFormat(value)
                val count: Int = format.getFormatsByArgumentIndex.length
                maxCount = Math.max(maxCount, count)
              }
              catch {
                case c: ControlFlowException => throw c
                case _: Exception =>
              }
            }
          }
        case _ =>
      }
    }
    maxCount
  }

  def isValidPropertyReference(expression: ScLiteral, key: String, outResourceBundle: Ref[String]): Boolean = {
    val annotationAttributeValues = new mutable.HashMap[String, AnyRef]
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
    if (mustBePropertyKey(expression, annotationAttributeValues)) {
      annotationAttributeValues.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER).exists {
        case bundleName: PsiElement =>
          val result = JavaPsiFacade.getInstance(bundleName.getProject).getConstantEvaluationHelper.computeConstantExpression(bundleName)
          if (result == null) false else {
            val bundleName = result.toString
            outResourceBundle.set(bundleName)
            isPropertyRef(expression, key, bundleName)
          }
        case _ => false
      }
    } else true
  }
}
