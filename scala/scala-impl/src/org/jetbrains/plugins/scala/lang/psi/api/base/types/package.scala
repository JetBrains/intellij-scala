package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author adkozlov
  */
package object types {

  implicit class ScTypeElementExt(val typeElement: ScTypeElement) extends AnyVal {
    def calcType: ScType = typeElement.`type`().getOrAny
    
    def getParamTypeText: String =
      if (typeElement.isRepeated) s"_root_.scala.collection.Seq[${typeElement.getText}]"
      else                        typeElement.getText
  }
}
