package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer

import java.{util => ju}

private final class WorksheetResNHighlightHandler(
  editor: Editor,
  file: PsiFile,
  elementAtCaret: PsiElement,
  referenced: Array[PsiElement]
) extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  override def selectTargets(
    targets: ju.List[_ <: PsiElement],
    selectionConsumer: Consumer[_ >: ju.List[_ <: PsiElement]]
  ): Unit = {
    selectionConsumer.consume(targets)
  }

  override def getTargets: ju.List[PsiElement] = {
    val list = new ju.ArrayList[PsiElement]
    list.add(elementAtCaret)
    referenced.foreach(list.add)
    list
  }

  override def computeUsages(targets: ju.List[_ <: PsiElement]): Unit = {
    if (targets.size >= 2) {
      myReadUsages.add(targets.get(0).getTextRange)
      targets.stream().skip(1).forEach { target =>
        myWriteUsages.add(target.getTextRange)
      }
    }
  }
}
