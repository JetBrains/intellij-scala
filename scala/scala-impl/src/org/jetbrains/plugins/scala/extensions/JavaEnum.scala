package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiClass, PsiEnumConstant}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum

object JavaEnum {
  // Scala enums use their declared cases, which need not have Java enum constant fields.
  def unapply(enumClass: PsiClass): Option[Seq[PsiEnumConstant]] =
    Option.when(enumClass.isEnum && !enumClass.is[ScEnum])(enumClass.getFields.toSeq.filterByType[PsiEnumConstant])
}
