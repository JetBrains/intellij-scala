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
import com.intellij.psi.util._
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings

import scala.collection.mutable

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

object ScalaI18nUtil {
  final val NULL: IProperty = new PropertyImpl(new PropertyStubImpl(null, null), PropertiesElementTypes.PROPERTY)
  private final val FOLD_MAX_LENGTH: Int = 50
  private final val CACHE: Key[IProperty] = Key.create("i18n.property.cache")
  private final val TOP_LEVEL_EXPRESSION: Key[ParameterizedCachedValue[ScExpression, (Project, ScExpression)]] = Key.create("TOP_LEVEL_EXPRESSION")
  private final val TOP_LEVEL_PROVIDER: ParameterizedCachedValueProvider[ScExpression, (Project, ScExpression)] =
    new ParameterizedCachedValueProvider[ScExpression, (Project, ScExpression)] {
      def compute(pair: (Project, ScExpression)): CachedValueProvider.Result[ScExpression] = {
        val param: ScExpression = pair._2
        val project: Project = pair._1
        val topLevel: ScExpression = getTopLevel(project, param)
        val cachedValue: ParameterizedCachedValue[ScExpression, (Project, ScExpression)] = param.getUserData(TOP_LEVEL_EXPRESSION)
        assert(cachedValue != null)
        var i: Int = 0
        var element: PsiElement = param
        while (element ne topLevel) {
          if (i % 10 == 0) {
            element.putUserData(TOP_LEVEL_EXPRESSION, cachedValue)
          }
          element = element.getParent
          i += 1
          i
        }
        CachedValueProvider.Result.create(topLevel, PsiManager.getInstance(project).getModificationTracker)
      }
    }

  def isFoldingsOn: Boolean = {
    ScalaCodeFoldingSettings.getInstance.isCollapseI18nMessages
  }

  def isI18nProperty(@NotNull project: Project, @NotNull expr: ScLiteral): Boolean = {
    if (!isStringLiteral(expr)) return false
    val property: IProperty = expr.getUserData(CACHE)
    if (property == NULL) return false
    if (property != null) return true
    val annotationParams = new mutable.HashMap[String, AnyRef]
    annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
    val isI18n: Boolean = mustBePropertyKey(project, expr, annotationParams)
    if (!isI18n) {
      expr.putUserData(CACHE, NULL)
    }
    isI18n
  }

  private def isStringLiteral(expr: ScLiteral): Boolean = {
    if (expr == null || expr.getText == null) return false
    val text: String = expr.getText
    text.startsWith("\"") && text.endsWith("\"") && text.length > 2
  }

  def mustBePropertyKey(@NotNull project: Project, @NotNull expression: ScLiteral,
                        @NotNull annotationAttributeValues: mutable.HashMap[String, AnyRef]): Boolean = {
    isPassedToAnnotatedParam(project, expression, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null)
  }

  def isPassedToAnnotatedParam(@NotNull project: Project, @NotNull myExpression: ScLiteral, annFqn: String,
                               @Nullable annotationAttributeValues: mutable.HashMap[String, AnyRef],
                               @Nullable nonNlsTargets: mutable.HashSet[PsiModifierListOwner]): Boolean = {
    val expression = getToplevelExpression(project, myExpression)
    val parent: PsiElement = expression.getParent
    if (!parent.isInstanceOf[ScArgumentExprList]) return false
    var idx: Int = -1
    val args: Array[ScExpression] = parent.asInstanceOf[ScArgumentExprList].exprsArray
    var i: Int = 0
    var flag = true
    while (i < args.length && flag) {
      val arg: ScExpression = args(i)
      if (PsiTreeUtil.isAncestor(arg, expression, false)) {
        idx = i
        flag = false
      }
      i += 1
      i
    }
    if (idx == -1) return false
    val grParent: PsiElement = parent.getParent
    grParent match {
      case methodCall: ScMethodCall =>
        methodCall.getInvokedExpr match {
          case refExpr: ScReferenceExpression =>
            val method = refExpr.resolve()
            method match {
              case psiMethod: PsiMethod =>
                if (method != null && isMethodParameterAnnotatedWith(psiMethod, idx, null, annFqn, annotationAttributeValues, nonNlsTargets)) {
                  return true
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    false
  }

  @NotNull def getToplevelExpression(@NotNull project: Project, @NotNull expression: ScExpression): ScExpression = {
    if (expression.isInstanceOf[PsiBinaryExpression] || expression.getParent.isInstanceOf[PsiBinaryExpression]) {
      return CachedValuesManager.getManager(project).getParameterizedCachedValue(expression, TOP_LEVEL_EXPRESSION, TOP_LEVEL_PROVIDER, true, (project, expression))
    }
    getTopLevel(project, expression)
  }

  @NotNull private def getTopLevel(project: Project, @NotNull myExpression: ScExpression): ScExpression = {
    var expression = myExpression
    var i: Int = 0
    var flag = true
    while (expression.getParent.isInstanceOf[ScExpression] && flag) {
      i += 1
      val parent: ScExpression = expression.getParent.asInstanceOf[ScExpression]
      parent match {
        case condExpr: PsiConditionalExpression if condExpr.getCondition == expression => flag = false
        case _ =>
      }
      expression = parent
      if (expression.isInstanceOf[PsiAssignmentExpression]) flag = false
      if (i > 10 && expression.isInstanceOf[PsiBinaryExpression]) {
        val value: ParameterizedCachedValue[ScExpression, (Project, ScExpression)] = expression.getUserData(TOP_LEVEL_EXPRESSION)
        if (value != null && value.hasUpToDateValue) {
          return getToplevelExpression(project, expression)
        }
      }
    }
    expression
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
    val params: Array[PsiParameter] = method.getParameterList.getParameters
    var param: PsiParameter = null
    if (idx >= params.length) {
      if (params.length == 0) {
        return false
      }
      val lastParam: PsiParameter = params(params.length - 1)
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
      import scala.collection.JavaConversions._
      for (propertiesFile <- propertiesFiles) {
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
            var flag = true
            if (result.isValidResult && result.getElement.isInstanceOf[IProperty]) {
              val value: String = result.getElement.asInstanceOf[IProperty].getValue
              var format: MessageFormat = null
              try {
                format = new MessageFormat(value)
              }
              catch {
                case e: Exception => {
                  flag = false
                }
              }
              if (flag) {
                try {
                  val count: Int = format.getFormatsByArgumentIndex.length
                  maxCount = Math.max(maxCount, count)
                }
                catch {
                  case ignored: IllegalArgumentException => {
                  }
                }
              }
            }
          }
        case _ =>
      }
    }
    maxCount
  }

  def formatMethodCallExpression(project: Project, methodCallExpression: ScMethodCall): String = {
    val args: Array[ScExpression] = methodCallExpression.args.exprsArray
    if (args.length > 0 && args(0).isInstanceOf[ScLiteral] && args(0).isValid &&
      isI18nProperty(project, args(0).asInstanceOf[ScLiteral])) {
      val count: Int = getPropertyValueParamsMaxCount(args(0).asInstanceOf[ScLiteral])
      if (args.length == 1 + count) {
        var text: String = getI18nMessage(project, args(0).asInstanceOf[ScLiteral])
        var i: Int = 1
        var flag = true
        while (i < count + 1 && flag) {
          val evaluator = new ScalaConstantExpressionEvaluator
          var value: AnyRef = evaluator.computeConstantExpression(args(i), throwExceptionOnOverflow = false)
          if (value == null) {
            if (args(i).isInstanceOf[ScReferenceExpression]) {
              value = "{" + args(i).getText + "}"
            }
            else {
              text = null
              flag = false
            }
          }
          text = text.replace("{" + (i - 1) + "}", value.toString)
          i += 1
          i
        }
        if (text != null) {
          if (!(text == methodCallExpression.getText)) {
            text = text.replace("''", "'")
          }
          return if (text.length > FOLD_MAX_LENGTH) text.substring(0, FOLD_MAX_LENGTH - 3) + "...\"" else text
        }
      }
    }
    methodCallExpression.getText
  }

  def isValidPropertyReference(@NotNull project: Project, @NotNull expression: ScLiteral, @NotNull key: String, @NotNull outResourceBundle: Ref[String]): Boolean = {
    val annotationAttributeValues = new mutable.HashMap[String, AnyRef]
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
    if (mustBePropertyKey(project, expression, annotationAttributeValues)) {
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
    import scala.collection.JavaConversions._
    for (file <- propertiesFiles) {
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
