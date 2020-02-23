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
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExpressionExt

class ScalaHighlightExprResultHandler(expr: ScExpression, editor: Editor,
                                      file: PsiFile, keyword: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  override def computeUsages(targets: util.List[PsiElement]): Unit = {
    val returns = expr.calculateTailReturns ++ Set(keyword)
    returns.map(_.getTextRange).foreach(myReadUsages.add)
  }

  override def selectTargets(targets: util.List[PsiElement], selectionConsumer: Consumer[util.List[PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def getTargets: util.List[PsiElement] = Collections.singletonList(keyword)
}
