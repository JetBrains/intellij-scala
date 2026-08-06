package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

/**
 * This condition is called when no available RenameHandler is found.
 * Normally the default rename handler is used then.
 * This class can prevent that and prevent any renaming on the given element.
 */
class ScalaVetoDefaultRenameCondition extends Condition[PsiElement]{
  override def value(t: PsiElement): Boolean = shouldNotRename(t)

  private def shouldNotRename(element: PsiElement): Boolean = element match {
    case _: ScEnd =>
      // We get ScEnd here if the cursor is located on the actual end-token of an end marker
      // In that case we don't want to rename anything.
      // This is in line with other tokens like `package`, `class`, `def`, etc.
      true
    case _ =>
      false
  }
}
