package org.jetbrains.plugins.scala.worksheet.actions.repl

import java.{util => ju}

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer

private final class WorksheetResNHighlightHandler(editor: Editor, file: PsiFile, el: PsiElement, referenced: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  override def selectTargets(targets: ju.List[PsiElement], selectionConsumer: Consumer[ju.List[PsiElement]]): Unit =
    selectionConsumer.consume(targets)

  override def getTargets: ju.List[PsiElement] =
    ju.Arrays.asList(el, referenced)

  override def computeUsages(targets: ju.List[PsiElement]): Unit =
    if (targets.size >= 2) {
      myReadUsages.add(targets.get(0).getTextRange)
      myWriteUsages.add(targets.get(1).getTextRange)
    }
}
