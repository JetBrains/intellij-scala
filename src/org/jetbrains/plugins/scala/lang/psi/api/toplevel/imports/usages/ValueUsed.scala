package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt

/**
 * @author Alexander Podkhalyuzin
 */

abstract sealed case class ValueUsed(e: PsiNamedElement) {
  override def toString: String = e.name
}

case class ReadValueUsed(override val e: PsiNamedElement) extends ValueUsed(e) {
  override def toString: String = "ValueRead(" + super.toString + ""
}

case class WriteValueUsed(override val e: PsiNamedElement) extends ValueUsed(e) {
  override def toString: String = "ValueWrite(" + super.toString + ""
}