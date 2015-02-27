package org.jetbrains.plugins.scala.codeInspection.suppression

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock

import scala.annotation.tailrec

/**
 * @author Nikolay.Tropin
 */
class SuppressByScalaCommentFix(key: HighlightDisplayKey) extends SuppressByCommentFix(key, classOf[ScBlockStatement]) {
  override def getContainer(context: PsiElement): PsiElement = {
    @tailrec
    def inner(elem: PsiElement): ScBlockStatement = {
      elem match {
        case (bs: ScBlockStatement) childOf (_: ScBlock | _: ScExtendsBlock | _: ScEarlyDefinitions) => bs
        case null => null
        case _ => inner(PsiTreeUtil.getParentOfType(elem, classOf[ScBlockStatement]))
      }
    }
    inner(context)
  }
}
