package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author adkozlov
  */
package object types {

  implicit class ScTypeElementExt(val typeElement: ScTypeElement) extends AnyVal {
    def calcType: ScType = typeElement.`type`().getOrAny
  }
}
