package org.jetbrains.plugins.scala.highlighter.usages

import java.util
import java.util.Collections

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.12.2009
 */

class ScalaHighlightExitPointsHandler(fun: ScFunctionDefinition, editor: Editor,
                                      file: PsiFile, keyword: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  override def computeUsages(targets: util.List[PsiElement]): Unit = {
    val usages = fun.returnUsages ++ Set(keyword)
    usages.map(_.getTextRange).foreach(myReadUsages.add)
  }

  override def selectTargets(targets: util.List[PsiElement], selectionConsumer: Consumer[util.List[PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def getTargets: util.List[PsiElement] = Collections.singletonList(keyword)
}
