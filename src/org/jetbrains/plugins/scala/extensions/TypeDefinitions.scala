package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScToplevelElement

/**
 * Pavel Fatin
 */

object TypeDefinitions {
  def unapplySeq(e: ScToplevelElement): Some[Seq[PsiClass]] = Some(e.typeDefinitions)
}