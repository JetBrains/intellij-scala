package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/**
 * @author Alexander Podkhalyuzin
 * @deprecated
 * Date : 05.05.2008
 */

private class Packaging(packaging: ScPackaging) extends AbstractTreeElement(packaging, inherited = false) {
  override def children: Seq[PsiElement] =
    packaging.immediateTypeDefinitions ++ packaging.packagings

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = true
}
