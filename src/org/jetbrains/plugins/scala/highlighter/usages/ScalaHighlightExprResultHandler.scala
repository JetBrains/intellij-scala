package org.jetbrains.plugins.scala
package highlighter
package usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiFile, PsiElement}
import java.util.{ArrayList, List}
import collection.JavaConversions._
import lang.psi.api.expr.{ScExpression, ScBlockExpr}


class ScalaHighlightExprResultHandler(expr: ScExpression, editor: Editor, file: PsiFile) extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
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

  def getTargets: List[PsiElement] = expr.calculateReturns.toBuffer[PsiElement]
}