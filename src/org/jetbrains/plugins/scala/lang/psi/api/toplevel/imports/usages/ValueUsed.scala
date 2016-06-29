package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages

import com.intellij.psi.{PsiNamedElement, SmartPointerManager, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions._

/**
 * @author Alexander Podkhalyuzin
 */

abstract sealed class ValueUsed(val pointer: ValueUsed.Pointer) {
  def isValid: Boolean = Option(pointer.getElement).exists(_.isValid)

  override def toString: String = Option(pointer.getElement).map(_.name).getOrElse("No element")
}

object ValueUsed {
  type Pointer = SmartPsiElementPointer[PsiNamedElement]

  def createPointer(e: PsiNamedElement): SmartPsiElementPointer[PsiNamedElement] = SmartPointerManager.getInstance(e.getProject).createSmartPsiElementPointer(e)

  def unapply(v: ValueUsed): Option[PsiNamedElement] = {
    Option(v.pointer.getElement)
  }
}

case class ReadValueUsed(override val pointer: ValueUsed.Pointer) extends ValueUsed(pointer) {
  override def toString: String = "ValueRead(" + super.toString + ")"
}

object ReadValueUsed {
  def apply(e: PsiNamedElement): ReadValueUsed = ReadValueUsed(ValueUsed.createPointer(e))
}

case class WriteValueUsed(override val pointer: ValueUsed.Pointer) extends ValueUsed(pointer) {
  override def toString: String = "ValueWrite(" + super.toString + ")"
}

object WriteValueUsed {
  def apply(e: PsiNamedElement): WriteValueUsed = WriteValueUsed(ValueUsed.createPointer(e))
}