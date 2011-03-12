package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import psi.types.ScType
import toplevel.typedef.ScMember

/**
 * A member that can be converted to a ScMethodType, ie a method or a constructor.
 */
trait ScMethodLike extends ScMember {
  def methodType: ScType = methodType(None)
  def methodType(result: Option[ScType]): ScType
}