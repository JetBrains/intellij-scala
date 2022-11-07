package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages

import com.intellij.model.Pointer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._

sealed trait ValueUsed {
  val declaration: Pointer[PsiNamedElement]
  val reference: Pointer[PsiElement]

  protected val name: String

  def isValid: Boolean = {
    def refersToValidElement(pointer: Pointer[_ <: PsiElement]): Boolean = {
      val element = pointer.dereference()
      element != null && element.isValid
    }

    refersToValidElement(declaration) && refersToValidElement(reference)
  }

  override final def toString: String = {
    val maybeName = Option(declaration.dereference()).map(_.name)
    s"$name(${maybeName.getOrElse("No element")})"
  }
}

case class ReadValueUsed(
  override val declaration: Pointer[PsiNamedElement],
  override val reference: Pointer[PsiElement]
) extends ValueUsed {
  override protected val name: String = "ValueRead"
}

object ValueUsed {
  val UseHardPointer: Boolean = RegistryManager.getInstance().is("scala.valueused.hardpointer")
}

object ReadValueUsed {
  def apply(declaration: PsiNamedElement, reference: PsiElement): ReadValueUsed =
    if (ValueUsed.UseHardPointer) {
      ReadValueUsed(Pointer.hardPointer(declaration), Pointer.hardPointer(reference))
    } else {
      ReadValueUsed(declaration.createSmartPointer, reference.createSmartPointer)
    }
}

case class WriteValueUsed(
  override val declaration: Pointer[PsiNamedElement],
  override val reference: Pointer[PsiElement]
) extends ValueUsed {
  override protected val name: String = "ValueWrite"
}

object WriteValueUsed {
  def apply(declaration: PsiNamedElement, reference: PsiElement): WriteValueUsed =
    if (ValueUsed.UseHardPointer) {
      WriteValueUsed(Pointer.hardPointer(declaration), Pointer.hardPointer(reference))
    } else {
      WriteValueUsed(declaration.createSmartPointer, reference.createSmartPointer)
    }
}
