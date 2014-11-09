package org.jetbrains.plugins.scala
package highlighter
package usages

import java.util.List

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

import scala.collection.JavaConversions._

/**
 * Highlights the expressions that will be evaluated during construction.
 */
class ScalaHighlightPrimaryConstructorExpressionsHandler(templateDef: ScTemplateDefinition, editor: Editor,
                                                         file: PsiFile, keyword: PsiElement) 
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  def computeUsages(targets: List[PsiElement]) {
    val iterator = targets.listIterator
    while (iterator.hasNext) {
      val elem = iterator.next
      myReadUsages.add(elem.getTextRange)
    }
  }

  def selectTargets(targets: List[PsiElement], selectionConsumer: Consumer[List[PsiElement]]) {
    selectionConsumer.consume(targets)
  }

  def getTargets: List[PsiElement] ={
    val eb = templateDef.extendsBlock
    val varAndValDefsExprs = eb.members.flatMap {
      case p: ScPatternDefinition => p.expr // we include lazy vals, perhaps they could be excluded.
      case v: ScVariableDefinition => v.expr
      case _ => None
    }
    val constructorExprs = varAndValDefsExprs ++ eb.templateBody.toList.flatMap(_.exprs) ++ Seq(keyword)
    constructorExprs.toBuffer[PsiElement]
  }
}