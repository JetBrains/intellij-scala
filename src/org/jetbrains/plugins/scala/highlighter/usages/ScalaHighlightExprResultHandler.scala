package org.jetbrains.plugins.scala
package highlighter
package usages

import java.util

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.JavaConversions._


class ScalaHighlightExprResultHandler(expr: ScExpression, editor: Editor,
                                      file: PsiFile, keyword: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  def computeUsages(targets: util.List[PsiElement]) {
    val iterator = targets.listIterator
    while (iterator.hasNext) {
      val elem = iterator.next
      myReadUsages.add(elem.getTextRange)
    }
  }

  def selectTargets(targets: util.List[PsiElement], selectionConsumer: Consumer[util.List[PsiElement]]) {
    selectionConsumer.consume(targets)
  }

  def getTargets: util.List[PsiElement] = {
    val returns = expr.calculateReturns() ++ Seq(keyword)
    returns.toBuffer[PsiElement]
  }
}