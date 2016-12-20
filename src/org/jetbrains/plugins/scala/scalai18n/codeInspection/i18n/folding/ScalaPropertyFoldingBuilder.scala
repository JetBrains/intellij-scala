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
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall, ScReferenceExpression}
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
    file.depthFirst()
      .filterByType(classOf[ScLiteral])
      .foreach(checkLiteral(project, _, result))

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

  def isCollapsedByDefault(@NotNull node: ASTNode): Boolean = ScalaI18nUtil.isFoldingsOn

  private def checkLiteral(project: Project, literal: ScLiteral, result: java.util.ArrayList[FoldingDescriptor]) {
    if (ScalaI18nUtil.isI18nProperty(project, literal)) {
      val property: IProperty = ScalaI18nUtil.getI18nProperty(project, literal)
      val set = new java.util.HashSet[AnyRef]
      if (property != null) set.add(property)
      literal.getParent match {
        case argsList: ScArgumentExprList =>
          val exprs = argsList.exprsArray
          val msg: String = ScalaI18nUtil.formatI18nProperty(literal, property)
          if (msg != literal.getText && (exprs(0) == literal)) {
            val count: Int = ScalaI18nUtil.getPropertyValueParamsMaxCount(literal)
            val args: Array[ScExpression] = argsList.exprsArray
            if (args.length == 1 + count && argsList.getParent.isInstanceOf[ScMethodCall]) {
              val evaluator = new ScalaConstantExpressionEvaluator
              val refOrValue = args.drop(1).forall { arg =>
                arg.isInstanceOf[ScReferenceExpression] ||
                  evaluator.computeConstantExpression(arg, throwExceptionOnOverflow = false) != null
              }
              if (refOrValue) {
                result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(argsList.getParent.getNode), argsList.getParent.getTextRange, null, set))
                return
              }
            }
          }
        case _ =>
      }
      result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(literal.getNode), literal.getTextRange, null, set))
    }
  }
}
