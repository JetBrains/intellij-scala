package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.openapi.util.TextRange
import java.util.{ArrayList, List}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.12.2009
 */

class ScalaHighlightExitPointsHandler(fun: ScFunctionDefinition, editor: Editor, file: PsiFile) extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  def computeUsages(targets: List[PsiElement]): Unit = {
    val iterator = targets.listIterator
    while (iterator.hasNext) {
      val elem = iterator.next
      myReadUsages.add(elem.getTextRange)
    }
  }

  def selectTargets(targets: List[PsiElement], selectionConsumer: Consumer[List[PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  def getTargets: List[PsiElement] = {
    val usages = fun.getReturnUsages
    val res = new ArrayList[PsiElement](usages.length)
    for (usage <- usages) res.add(usage)
    res
  }
}