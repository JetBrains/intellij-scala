package org.jetbrains.plugins.scala
package highlighter
package usages

import java.util
import java.util.Collections

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.ScMarkerOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExpressionExt

class ScalaHighlightExprResultHandler(expr: ScExpression, editor: Editor,
                                      file: PsiFile, keyword: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    val returns = expr.calculateTailReturns ++ (keyword match {
      case Parent(ScMarkerOwner(_, Some(_))) => Set.empty // Highlight as "brace" rather than "usage" (in ScalaBlockSupportHandler)
      case _ => Set(keyword)
    })
    returns.map(_.getTextRange).foreach(myReadUsages.add)
  }
  
  override def selectTargets(targets: util.List[_ <: PsiElement], selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def getTargets: util.List[PsiElement] = Collections.singletonList(keyword)

  // Cooperate with ScalaBlockSupportHandler
  override def highlightReferences(): Boolean = true
}
