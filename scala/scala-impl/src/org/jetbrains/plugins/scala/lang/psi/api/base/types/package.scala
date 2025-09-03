package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

package object types {
  implicit class TypeTreeHolderExt(private val typeTreeHolder: TypeTreeHolder) extends AnyVal {
    def calcType: ScType = typeTreeHolder.`type`().getOrAny
  }

  implicit class ScTypeElementExt(private val typeElement: ScTypeElement) extends AnyVal {
    def getParamTypeText: String =
      if (typeElement.isRepeated) s"_root_.scala.Seq[${typeElement.getText}]"
      else                        typeElement.getText
  }
}
