package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package packaging

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 */

trait ScPackageContainer extends ScalaPsiElement {

  def prefix : String
  def ownNamePart : String

  def fqn = concat(prefix, ownNamePart)

  def isExplicit: Boolean

  protected def concat(prefix : String, suffix : String) = if (prefix.length > 0) prefix + "." + suffix else suffix 

  def packagings: Seq[ScPackaging]

  def typeDefs: Seq[ScTypeDefinition]

}