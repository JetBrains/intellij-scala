package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.light.scala.ScLightFieldId
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.annotation.tailrec

/**
  * @author ilyas
  */
//wrapper over an identifier for variable declarations 'var v : T' 
trait ScFieldId extends ScTypedDefinition

object ScFieldId {
  @tailrec
  def getCompoundCopy(rt: ScType, f: ScFieldId): ScFieldId = {
    f match {
      case light: ScLightFieldId => getCompoundCopy(rt, light.f)
      case definition: ScFieldId => new ScLightFieldId(rt, definition)
    }
  }
}