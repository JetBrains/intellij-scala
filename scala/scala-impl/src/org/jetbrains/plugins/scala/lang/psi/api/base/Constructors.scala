package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

object Constructor {
  def unapply(e: PsiMethod): Option[PsiMethod] =
    if (e.isConstructor) Some(e) else None

  object ofClass {
    def unapply(arg: PsiMethod): Option[PsiClass] = arg match {
      case Constructor(constr) => Option(constr.containingClass)
      case _ => None
    }
  }
}

object ScalaConstructor {
  def unapply(arg: ScMethodLike): Option[ScMethodLike] =
    if (arg.isConstructor) Some(arg) else None

  object in {
    def unapply(arg: ScMethodLike): Option[ScTemplateDefinition] =
      if (arg.isConstructor) Option(arg.containingClass) else None
  }
}

object AuxiliaryConstructor {
  def unapply(arg: ScFunction): Option[ScFunction] =
    if (arg.isConstructor) Some(arg) else None

  object in {
    def unapply(arg: ScFunction): Option[ScTemplateDefinition] =
      if (arg.isConstructor) Option(arg.containingClass) else None
  }
}

object JavaConstructor {
  def unapply(arg: PsiMethod): Option[PsiMethod] =
    if (arg.isConstructor && !arg.isInstanceOf[ScMethodLike]) Some(arg) else None
}