package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import typedef.ScTypeDefinition

/**
 * @author ilyas
 */

trait ScPackageContainer extends ScalaPsiElement {

  def prefix : String
  def ownNamePart : String

  def fqn = {
    val _prefix = prefix
    if (_prefix.length > 0) _prefix + "." + ownNamePart else ownNamePart
  }

  def packagings: Seq[ScPackaging]

  def typeDefs: Seq[ScTypeDefinition]

}