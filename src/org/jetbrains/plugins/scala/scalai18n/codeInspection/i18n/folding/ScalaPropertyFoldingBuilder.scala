package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.{FoldingBuilderEx, FoldingDescriptor}
import com.intellij.lang.properties.IProperty
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.util.ObjectUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.util.ScalaConstantExpressionEvaluator
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaPropertyFoldingBuilder extends FoldingBuilderEx {

  @NotNull def buildFoldRegions(@NotNull element: PsiElement, @NotNull document: Document, quick: Boolean): Array[FoldingDescriptor] = {
    if (!element.isInstanceOf[ScalaFile] || quick || !ScalaI18nUtil.isFoldingsOn) {
      return FoldingDescriptor.EMPTY
    }
    val file: ScalaFile = element.asInstanceOf[ScalaFile]
    val project: Project = file.getProject
    val result = new java.util.ArrayList[FoldingDescriptor]
    file.accept(new ScalaRecursiveElementVisitor {
      override def visitLiteral(expression: ScLiteral) {
        checkLiteral(project, expression, result)
      }
    })
    result.toArray(new Array[FoldingDescriptor](result.size))
  }

  def getPlaceholderText(@NotNull node: ASTNode): String = {
    val element: PsiElement = SourceTreeToPsiMap.treeElementToPsi(node)
    element match {
      case literal: ScLiteral =>
        return ScalaI18nUtil.getI18nMessage(element.getProject, literal)
      case methodCall: ScMethodCall =>
        return ScalaI18nUtil.formatMethodCallExpression(element.getProject, methodCall)
      case _ =>
    }
    element.getText
  }

  def isCollapsedByDefault(@NotNull node: ASTNode): Boolean = {
    ScalaI18nUtil.isFoldingsOn
  }

  private def checkLiteral(project: Project, expression: ScLiteral, result: java.util.ArrayList[FoldingDescriptor]) {
    if (ScalaI18nUtil.isI18nProperty(project, expression)) {
      val property: IProperty = ScalaI18nUtil.getI18nProperty(project, expression)
      val set = new java.util.HashSet[AnyRef]
      if (property != null) set.add(property)
      val msg: String = ScalaI18nUtil.formatI18nProperty(expression, property)
      val parent: PsiElement = expression.getParent
      parent match {
        case expressions: ScArgumentExprList =>
          val exprs = expressions.exprsArray
          if (!(msg == expression.getText) && (exprs(0) eq expression)) {
            val count: Int = ScalaI18nUtil.getPropertyValueParamsMaxCount(expression)
            val args: Array[ScExpression] = expressions.exprsArray
            if (args.length == 1 + count && parent.getParent.isInstanceOf[ScMethodCall]) {
              var ok: Boolean = true
              var i: Int = 1
              while (i < count + 1 && ok) {
                val evaluator = new ScalaConstantExpressionEvaluator
                val value: AnyRef = evaluator.computeConstantExpression(args(i), throwExceptionOnOverflow = false)
                if (value == null) {
                  if (!args(i).isInstanceOf[ScReferenceExpression]) {
                    ok = false
                  }
                }
                i += 1
                i
              }
              if (ok) {
                result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(parent.getParent.getNode), parent.getParent.getTextRange, null, set))
                return
              }
            }
          }
        case _ =>
      }
      result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(expression.getNode), expression.getTextRange, null, set))
    }
  }
}
