package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n

import java.text.MessageFormat

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.{IProperty, PropertiesImplUtil, PropertiesReferenceManager}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.{PsiMethodExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall}

import scala.collection.mutable

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

object ScalaI18nUtil {
  def mustBePropertyKey(@NotNull literal: ScLiteral,
                        @Nullable annotationAttributeValues: mutable.HashMap[String, AnyRef] = null): Boolean = {
    mayBePropertyKey(literal) && isPassedToAnnotatedParam(literal, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null)
  }

  private def mayBePropertyKey(literal: ScLiteral): Boolean = literal match {
    case ScStringLiteral(string) => !string.exists {
      case '=' | ':' => true
      case character => Character.isWhitespace(character)
    }
    case _ => false
  }

  def isPassedToAnnotatedParam(@NotNull literal: ScLiteral, annFqn: String,
                               @Nullable annotationAttributeValues: mutable.HashMap[String, AnyRef],
                               @Nullable nonNlsTargets: mutable.HashSet[PsiModifierListOwner]): Boolean = {
    literal.getParent match {
      case argList: ScArgumentExprList =>
        val idx = argList.exprs.indexOf(literal)
        if (idx == -1) return false

        argList.getParent match {
          case ScMethodCall(ResolvesTo(method: PsiMethod), _) =>
            isMethodParameterAnnotatedWith(method, idx, null, annFqn, annotationAttributeValues, nonNlsTargets)
          case _ =>
            false
        }
      case _ => false
    }
  }

  def isMethodParameterAnnotatedWith(method: PsiMethod, idx: Int, @Nullable myProcessed: mutable.HashSet[PsiMethod],
                                     annFqn: String, @Nullable annotationAttributeValues: mutable.HashMap[String, AnyRef],
                                     @Nullable nonNlsTargets: mutable.HashSet[PsiModifierListOwner]): Boolean = {
    var processed = myProcessed
    if (processed != null) {
      if (processed.contains(method)) return false
    }
    else {
      processed = new mutable.HashSet[PsiMethod]
    }
    processed.add(method)
    val params = method.parameters
    var param: PsiParameter = null
    if (idx >= params.length) {
      if (params.isEmpty) {
        return false
      }
      val lastParam: PsiParameter = params.last
      if (lastParam.isVarArgs) {
        param = lastParam
      }
      else {
        return false
      }
    }
    else {
      param = params(idx)
    }
    val annotation: PsiAnnotation = AnnotationUtil.findAnnotation(param, annFqn)
    if (annotation != null) {
      if (annotationAttributeValues != null) {
        val parameterList: PsiAnnotationParameterList = annotation.getParameterList
        val attributes: Array[PsiNameValuePair] = parameterList.getAttributes
        for (attribute <- attributes) {
          val name: String = attribute.getName
          if (annotationAttributeValues.contains(name)) {
            annotationAttributeValues.put(name, attribute.getValue)
          }
        }
      }
      return true
    }
    if (nonNlsTargets != null) {
      nonNlsTargets.add(param)
    }
    val superMethods: Array[PsiMethod] = method.findSuperMethods
    for (superMethod <- superMethods) {
      if (isMethodParameterAnnotatedWith(superMethod, idx, processed, annFqn, annotationAttributeValues, null)) return true
    }
    false
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

  @NotNull def propertiesFilesByBundleName(resourceBundleName: String, context: PsiElement): java.util.List[PropertiesFile] = {
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
                case _: Exception =>
              }
            }
          }
        case _ =>
      }
    }
    maxCount
  }

  def isValidPropertyReference(@NotNull project: Project, @NotNull expression: ScLiteral, @NotNull key: String, @NotNull outResourceBundle: Ref[String]): Boolean = {
    val annotationAttributeValues = new mutable.HashMap[String, AnyRef]
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
    if (mustBePropertyKey(expression, annotationAttributeValues)) {
      annotationAttributeValues get AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER exists {
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
