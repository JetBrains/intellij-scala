package org.jetbrains.plugins.scala
package highlighter
package usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

import java.util
import java.util.Collections

/**
  * Highlights the expressions that will be evaluated during construction.
  */
class ScalaHighlightPrimaryConstructorExpressionsHandler(templateDef: ScTemplateDefinition, editor: Editor,
                                                         file: PsiFile, keyword: PsiElement)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    val eb = templateDef.extendsBlock
    val varAndValDefsExprs = eb.members.flatMap {
      case p: ScPatternDefinition => p.expr.toList // we include lazy vals, perhaps they could be excluded.
      case v: ScVariableDefinition => v.expr.toList
      case _ => Seq.empty
    }
    val usages = varAndValDefsExprs ++ eb.templateBody.toList.flatMap(_.exprs) :+ keyword
    usages.map(_.getTextRange).foreach(myReadUsages.add)
  }

  override def selectTargets(targets: util.List[_ <: PsiElement], selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def getTargets: util.List[PsiElement] = Collections.singletonList(keyword)
}
