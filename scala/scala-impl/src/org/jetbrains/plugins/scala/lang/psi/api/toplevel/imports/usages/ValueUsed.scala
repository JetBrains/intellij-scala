package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages

import com.intellij.psi.{PsiNamedElement, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions._

/**
  * @author Alexander Podkhalyuzin
  */
sealed trait ValueUsed {
  val pointer: SmartPsiElementPointer[PsiNamedElement]

  protected val name: String

  def isValid: Boolean = pointer match {
    case ValidSmartPointer(_) => true
    case _ => false
  }

  override final def toString: String = {
    val maybeName = Option(pointer.getElement).map(_.name)
    s"$name(${maybeName.getOrElse("No element")})"
  }
}

object ValueUsed {

  def unapply(v: ValueUsed): Option[PsiNamedElement] = {
    Option(v.pointer.getElement)
  }
}

case class ReadValueUsed(pointer: SmartPsiElementPointer[PsiNamedElement]) extends ValueUsed {
  override protected val name: String = "ValueRead"
}

object ReadValueUsed {
  def apply(e: PsiNamedElement): ReadValueUsed = ReadValueUsed(e.createSmartPointer)
}

case class WriteValueUsed(pointer: SmartPsiElementPointer[PsiNamedElement]) extends ValueUsed {
  override protected val name: String = "ValueWrite"
}

object WriteValueUsed {
  def apply(e: PsiNamedElement): WriteValueUsed = WriteValueUsed(e.createSmartPointer)
}