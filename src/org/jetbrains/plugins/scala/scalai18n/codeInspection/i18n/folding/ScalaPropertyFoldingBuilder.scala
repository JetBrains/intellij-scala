package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.folding

import com.intellij.lang.folding.{FoldingDescriptor, FoldingBuilderEx}
import com.intellij.lang.properties.IProperty
import com.intellij.openapi.editor.Document
import com.intellij.psi._
import com.intellij.openapi.project.Project
import impl.source.SourceTreeToPsiMap
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil
import collection.mutable.ArrayBuffer
import org.jetbrains.annotations.NotNull
import com.intellij.util.ObjectUtils
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScExpression, ScArgumentExprList}
import com.intellij.lang.ASTNode

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaPropertyFoldingBuilder extends FoldingBuilderEx {

  @NotNull def buildFoldRegions(@NotNull element: PsiElement, @NotNull document: Document, quick: Boolean): Array[FoldingDescriptor] = {
    if (!(element.isInstanceOf[ScalaFile]) || quick || !ScalaI18nUtil.isFoldingsOn) {
      return FoldingDescriptor.EMPTY
    }
    val file: ScalaFile = element.asInstanceOf[ScalaFile]
    val project: Project = file.getProject
    val result = new ArrayBuffer[FoldingDescriptor]
    file.accept(new ScalaRecursiveElementVisitor {
      override def visitLiteral(expression: ScLiteral) {
        checkLiteral(project, expression, result)
      }
    })
    result.toArray
  }

  def getPlaceholderText(@NotNull node: ASTNode): String = {
    val element: PsiElement = SourceTreeToPsiMap.treeElementToPsi(node)
    if (element.isInstanceOf[ScLiteral]) {
      return ScalaI18nUtil.getI18nMessage(element.getProject, element.asInstanceOf[ScLiteral])
    }
    else if (element.isInstanceOf[ScMethodCall]) {
      return ScalaI18nUtil.formatMethodCallExpression(element.getProject, element.asInstanceOf[ScMethodCall])
    }
    element.getText
  }

  def isCollapsedByDefault(@NotNull node: ASTNode): Boolean = {
    ScalaI18nUtil.isFoldingsOn
  }

  private def checkLiteral(project: Project, expression: ScLiteral, result: ArrayBuffer[FoldingDescriptor]) {
    if (ScalaI18nUtil.isI18nProperty(project, expression)) {
      val property: IProperty = ScalaI18nUtil.getI18nProperty(project, expression)
      val set = new java.util.HashSet[AnyRef]
      set.add(property)
      val msg: String = ScalaI18nUtil.formatI18nProperty(expression, property)
      val parent: PsiElement = expression.getParent
      if (!(msg == expression.getText) && parent.isInstanceOf[ScArgumentExprList] && (parent.asInstanceOf[ScArgumentExprList]).exprs(0) == expression) {
        val expressions: ScArgumentExprList = parent.asInstanceOf[ScArgumentExprList]
        val count: Int = ScalaI18nUtil.getPropertyValueParamsMaxCount(expression)
        val args: Array[ScExpression] = expressions.exprsArray
        if (args.length == 1 + count && parent.getParent.isInstanceOf[ScMethodCall]) {
          result.+=(new FoldingDescriptor(ObjectUtils.assertNotNull(parent.getParent.getNode), parent.getParent.getTextRange, null, set))
          return
        }
      }
      result.+=(new FoldingDescriptor(ObjectUtils.assertNotNull(expression.getNode), expression.getTextRange, null, set))
    }
  }
}
