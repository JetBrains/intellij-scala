package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScBraceOwnerBase extends ScalaPsiElementBase { this: ScBraceOwner =>
  def isEnclosedByBraces: Boolean
}