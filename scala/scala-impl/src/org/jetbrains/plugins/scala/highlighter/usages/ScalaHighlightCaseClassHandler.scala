package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesConfiguration, ScalaFindUsagesHandler}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

import java.util

class ScalaHighlightCaseClassHandler(reference: ScReference, caseClass: ScClass, file: PsiFile, editor: Editor)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file)
{
  override def getTargets: util.List[PsiElement] = util.Collections.singletonList(reference)

  override def selectTargets(targets: util.List[_ <: PsiElement], selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit =
    selectionConsumer.consume(targets)

  override protected def addOccurrence(element: PsiElement): Unit = {
    if (element != null && element.getContainingFile == file)
      super.addOccurrence(element match {
        case ref: ScStableCodeReference => ref.nameId
        case e => e
      })
  }

  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    val config = ScalaFindUsagesConfiguration.getInstance(file.getProject)
    val manager = new ScalaFindUsagesHandler(caseClass, config)
    val localSearchScope = new LocalSearchScope(file)

    addOccurrence(caseClass.nameId)

    //highlight references to the constructor or class
    val references = manager.findReferencesToHighlight(caseClass, localSearchScope)
    references.forEach { ref =>
      val occurenceElement = ref match {
        case si: ScSelfInvocation => si.thisElement
        case _ => ref.getElement
      }
      addOccurrence(occurenceElement)
    }
  }
}
