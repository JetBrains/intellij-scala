package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiClass, PsiEnumConstant}

object JavaEnum {
  def unapply(enumClass: PsiClass): Option[Seq[PsiEnumConstant]] =
    Option.when(enumClass.isEnum)(enumClass.getFields.toSeq.filterByType[PsiEnumConstant])
}
