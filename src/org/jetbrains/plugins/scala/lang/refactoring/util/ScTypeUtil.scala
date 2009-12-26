package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import psi.types.{ScParameterizedType, ScType}


/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2009
 */

object ScTypeUtil {
  //for java
  def presentableText(typez: ScType) = ScType.presentableText(typez)

  def stripTypeArgs(tp: ScType): ScType = tp match {
    case ScParameterizedType(designator, _) => designator
    case t => t
  }
}