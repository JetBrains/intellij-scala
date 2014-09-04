package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions._

/**
 * @author Alexander Podkhalyuzin
 */

abstract sealed class ValueUsed(val e: PsiNamedElement) {
  override def toString: String = e.name
}

object ValueUsed {
  def unapply(v: ValueUsed): Option[PsiNamedElement] = {
    Some(v.e)
  }
}

case class ReadValueUsed(override val e: PsiNamedElement) extends ValueUsed(e) {
  override def toString: String = "ValueRead(" + super.toString + ""
}

case class WriteValueUsed(override val e: PsiNamedElement) extends ValueUsed(e) {
  override def toString: String = "ValueWrite(" + super.toString + ""
}