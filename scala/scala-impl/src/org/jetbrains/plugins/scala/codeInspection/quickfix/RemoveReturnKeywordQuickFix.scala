package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturn

final class RemoveReturnKeywordQuickFix(r: ScReturn) extends AbstractFixOnPsiElement(ScalaBundle.message("remove.return.keyword"), r) {

  override protected def doApplyFix(ret: ScReturn)(implicit project: Project): Unit =
    ret.expr match {
      case Some(e) => ret.replace(e.copy())
      case None => ret.delete()
    }

}
