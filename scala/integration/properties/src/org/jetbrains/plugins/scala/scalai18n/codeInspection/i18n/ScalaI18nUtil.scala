package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n

import java.text.MessageFormat

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.parsing.PropertiesElementTypes
import com.intellij.lang.properties.psi.impl.{PropertyImpl, PropertyStubImpl}
import com.intellij.lang.properties.psi.{PropertiesFile, Property}
import com.intellij.lang.properties.{IProperty, PropertiesImplUtil, PropertiesReferenceManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Key, Ref, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiMethodExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScReferenceElement, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings
import scala.collection.mutable

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

object ScalaI18nUtil {
  final val NULL: IProperty = new PropertyImpl(new PropertyStubImpl(null, null), PropertiesElementTypes.PROPERTY)
  private val FOLD_MAX_LENGTH: Int = 50
  private val CACHE: Key[IProperty] = Key.create("i18n.property.cache")

  def isFoldingsOn: Boolean = {
    ScalaCodeFoldingSettings.getInstance.isCollapseI18nMessages
  }

  def isI18nProperty(@NotNull project: Project, @NotNull literal: ScLiteral): Boolean = {
    if (!mayBePropertyKey(literal)) return false
    val property: IProperty = literal.getUserData(CACHE)
    if (property == NULL) return false
    if (property != null) return true

    val annotationAttributeValues = new mutable.HashMap[String, AnyRef]
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)

    val isI18n: Boolean = mustBePropertyKey(literal, annotationAttributeValues)

    if (!isI18n) literal.putUserData(CACHE, NULL)
    isI18n
  }

  def mustBePropertyKey(@NotNull literal: ScLiteral,
                        @NotNull annotationAttributeValues: mutable.HashMap[String, AnyRef]): Boolean = {
    mayBePropertyKey(literal) && isPassedToAnnotatedParam(literal, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null)
  }

  def mayBePropertyKey(literal: ScLiteral): Boolean = {
    def isForbiddenInKey(c: Char) = c == '=' || c == ':' || Character.isWhitespace(c)

    literal match {
      case ScStringLiteral(value) => !value.exists(isForbiddenInKey)
      case _ => false
    }
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

  @NotNull def getMaxExpression(@NotNull expression: ScExpression): ScExpression = {
    val exprs = expression.withParentsInFile.takeWhile(_.isInstanceOf[ScExpression])
    exprs.toSeq.last.asInstanceOf[ScExpression]
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

  def getI18nMessage(@NotNull project: Project, literal: ScLiteral): String = {
    val property: IProperty = getI18nProperty(project, literal)
    if (property == null) literal.getText else formatI18nProperty(literal, property)
  }

  @Nullable def getI18nProperty(project: Project, literal: ScLiteral): IProperty = {
    val property: Property = literal.getUserData(CACHE).asInstanceOf[Property]
    if (property eq NULL) return null
    if (property != null && isValid(property, literal)) return property
    if (isI18nProperty(project, literal)) {
      val references: Array[PsiReference] = literal.getReferences
      for (reference <- references) {
        reference match {
          case polyVarRef: PsiPolyVariantReference =>
            val results: Array[ResolveResult] = polyVarRef.multiResolve(false)
            for (result <- results) {
              val element: PsiElement = result.getElement
              element match {
                case p: IProperty =>
                  literal.putUserData(CACHE, p)
                  return p
                case _ =>
              }
            }
          case _ =>
            val element: PsiElement = reference.resolve
            element match {
              case p: IProperty =>
                literal.putUserData(CACHE, p)
                return p
              case _ =>
            }
        }
      }
    }
    null
  }

  def formatI18nProperty(literal: ScLiteral, property: IProperty): String = {
    if (property == null) literal.getText else "\"" + property.getValue + "\""
  }

  private def isValid(property: Property, literal: ScLiteral): Boolean = {
    if (literal == null || property == null || !property.isValid) return false
    StringUtil.unquoteString(literal.getText) == property.getKey
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

  def formatMethodCallExpression(project: Project, methodCallExpression: ScMethodCall): String = {
    def argText(expr: ScExpression): String = {
      val evaluator = new ScalaConstantExpressionEvaluator
      Option(evaluator.computeConstantExpression(expr, throwExceptionOnOverflow = false)).getOrElse {
        "{" + expr.getText + "}"
      }.toString
    }
    def placeholder(idx: Int) = s"{${idx - 1}}"

    val defaultText = methodCallExpression.getText

    methodCallExpression.args.exprs match {
      case args @ Seq(lit: ScLiteral, _*) if lit.isValid && isI18nProperty(project, lit) =>
        val count: Int = getPropertyValueParamsMaxCount(lit)
        if (args.size == 1 + count) {
          var text: String = getI18nMessage(project, lit)
          for {
            (arg, idx) <- args.zipWithIndex.drop(1)
            value = argText(arg)
          } {
            text = text.replace(placeholder(idx), value.toString)
          }

          if (text != defaultText) {
            text = text.replace("''", "'")
          }

          if (text.length > FOLD_MAX_LENGTH)  text.substring(0, FOLD_MAX_LENGTH - 3) + "...\""
          else text
        }
        else defaultText
      case _ =>
        defaultText
    }
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

  def createProperty(project: Project, propertiesFiles: java.util.Collection[PropertiesFile], key: String, value: String) {
    propertiesFiles.forEach { file =>
      val documentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)
      documentManager.commitDocument(documentManager.getDocument(file.getContainingFile))
      val existingProperty: IProperty = file.findPropertyByKey(key)
      if (existingProperty == null) {
        file.addProperty(key, value)
      }
    }
  }

  @Nullable def getSelectedRange(editor: Editor, psiFile: PsiFile): TextRange = {
    if (editor == null) return null
    val selectedText: String = editor.getSelectionModel.getSelectedText
    if (selectedText != null) {
      return new TextRange(editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
    val psiElement: PsiElement = psiFile.findElementAt(editor.getCaretModel.getOffset)
    if (psiElement == null || psiElement.isInstanceOf[PsiWhiteSpace]) return null
    psiElement.getTextRange
  }

}
