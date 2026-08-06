package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import java.util
import java.util.Collections

class ScalaHighlightExitPointsHandler(fun: ScFunctionDefinition, editor: Editor,
                                      file: PsiFile, keyword: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    val usages = fun.returnUsages ++ (keyword match {
      case Parent(ScBegin(_, Some(_))) if CodeInsightSettings.getInstance.HIGHLIGHT_BRACES => Set.empty // Highlight as "brace" rather than "usage" (in ScalaBlockSupportHandler)
      case _ => Set(keyword)
    })
    usages.map(_.getTextRange).foreach(myReadUsages.add)
  }

  override def selectTargets(targets: util.List[_ <: PsiElement],
                             selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def getTargets: util.List[PsiElement] = Collections.singletonList(keyword)

  // Cooperate with ScalaBlockSupportHandler
  override def highlightReferences(): Boolean = true
}
