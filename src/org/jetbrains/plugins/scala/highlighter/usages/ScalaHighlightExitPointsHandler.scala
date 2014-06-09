package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiFile, PsiElement}
import java.util

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.12.2009
 */

class ScalaHighlightExitPointsHandler(fun: ScFunctionDefinition, editor: Editor,
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
    val usages = fun.returnUsages()
    val res = new util.ArrayList[PsiElement](usages.length)
    for (usage <- usages) res.add(usage)
    res.add(keyword)
    res
  }
}