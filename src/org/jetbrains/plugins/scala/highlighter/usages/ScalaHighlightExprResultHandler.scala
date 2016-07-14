package org.jetbrains.plugins.scala
package highlighter
package usages

import java.util
import java.util.Collections

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

class ScalaHighlightExprResultHandler(expr: ScExpression, editor: Editor,
                                      file: PsiFile, keyword: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  def computeUsages(targets: util.List[PsiElement]) {
    val returns = expr.calculateReturns() :+ keyword
    returns.map(_.getTextRange).foreach(myReadUsages.add)
  }

  def selectTargets(targets: util.List[PsiElement], selectionConsumer: Consumer[util.List[PsiElement]]) {
    selectionConsumer.consume(targets)
  }

  def getTargets: util.List[PsiElement] = Collections.singletonList(keyword)
}
