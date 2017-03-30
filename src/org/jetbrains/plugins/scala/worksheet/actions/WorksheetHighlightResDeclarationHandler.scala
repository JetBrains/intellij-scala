package org.jetbrains.plugins.scala.worksheet.actions

import java.util

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer

/**
  * User: Dmitry.Naydanov
  * Date: 30.03.17.
  */
class WorksheetHighlightResDeclarationHandler(editor: Editor, file: PsiFile, el: PsiElement, referenced: PsiElement) extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  override def selectTargets(targets: util.List[PsiElement], 
                             selectionConsumer: Consumer[util.List[PsiElement]]): Unit = selectionConsumer.consume(targets)
  override def getTargets: util.List[PsiElement] = util.Arrays.asList(el, referenced)

  override def computeUsages(targets: util.List[PsiElement]): Unit = {
    if (targets.size() < 2) return
    myReadUsages.add(targets.get(0).getTextRange)
    myWriteUsages.add(targets.get(1).getTextRange)
  }
}
