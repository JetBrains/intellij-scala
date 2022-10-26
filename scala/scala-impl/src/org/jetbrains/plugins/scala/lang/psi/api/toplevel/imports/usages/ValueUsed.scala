package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages

import com.intellij.psi.{PsiElement, PsiNamedElement, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions._

sealed trait ValueUsed {
  val declaration: SmartPsiElementPointer[PsiNamedElement]
  val reference: SmartPsiElementPointer[PsiElement]

  protected val name: String

  def isValid: Boolean = (declaration, reference) match {
    case (ValidSmartPointer(_), ValidSmartPointer(_)) => true
    case _ => false
  }

  override final def toString: String = {
    val maybeName = Option(declaration.getElement).map(_.name)
    s"$name(${maybeName.getOrElse("No element")})"
  }
}

case class ReadValueUsed(
  override val declaration: SmartPsiElementPointer[PsiNamedElement],
  override val reference: SmartPsiElementPointer[PsiElement]
) extends ValueUsed {
  override protected val name: String = "ValueRead"
}

object ReadValueUsed {
  def apply(declaration: PsiNamedElement, reference: PsiElement): ReadValueUsed =
    ReadValueUsed(declaration.createSmartPointer, reference.createSmartPointer)
}

case class WriteValueUsed(
  override val declaration: SmartPsiElementPointer[PsiNamedElement],
  override val reference: SmartPsiElementPointer[PsiElement]
) extends ValueUsed {
  override protected val name: String = "ValueWrite"
}

object WriteValueUsed {
  def apply(declaration: PsiNamedElement, reference: PsiElement): WriteValueUsed =
    WriteValueUsed(declaration.createSmartPointer, reference.createSmartPointer)
}
