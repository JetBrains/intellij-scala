package org.jetbrains.plugins.scala.worksheet.actions.repl

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.worksheet.{GotoOriginalHandlerUtil, WorksheetFile}

final class WorksheetResNGotoHandler extends GotoDeclarationHandler {

  override def getGotoDeclarationTargets(sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (sourceElement == null)
      null
    else {
      sourceElement.getContainingFile match {
        case file: WorksheetFile if ResNUtils.isResNSupportedInFile(file) =>
          val referenced = WorksheetResNGotoHandler.findReferencedPsi(sourceElement.getParent)
          referenced.orNull
        case _ =>
          null
      }
    }
  }

  override def getActionText(context: DataContext): String = null
}

private object WorksheetResNGotoHandler {

  private [repl] def findReferencedPsi(psiElement: PsiElement): Option[Array[PsiElement]] =
    for {
      ref <- Option(psiElement).filterByType[ScReferenceExpression]
      target <- findReferencedPsi(ref)
    } yield target


  /**
   * We are using `multiResolveScala` instead of `resolve` because there might be
   * explicitly-defined `resN` values in user code.
   * `resolve` would select only definition in the file
   *
   *
   * @example {{{
   *    val res1 = 1
   *    val res2 = 2
   *    val res3 = 3
   *
   *    res3
   *    res2
   *    res1
   * }}}
   * @see SCL-20478
   */
  private def findReferencedPsi(ref: ScReferenceExpression): Option[Array[PsiElement]] = {
    val resolvedElements: Array[PsiElement] = ref.multiResolveScala(false).map(_.element)
    val resolvedResNCandidate = resolvedElements.find { r =>
      val parent = r.getParent
      parent != null && !parent.isPhysical
    }

    val resolvedElementWithResNTarget = for {
      resolved <- resolvedResNCandidate
      target <- GotoOriginalHandlerUtil.getGoToTarget2(resolved)
      if target.isValid
    } yield (resolved, target)

    resolvedElementWithResNTarget.map { case (element, target) =>
      val result = resolvedElements.map(el => if (el eq element) target else el)
      result.sortBy(_.getNode.getStartOffset)
    }
  }
}
