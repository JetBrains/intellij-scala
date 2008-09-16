package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import typedef.ScTypeDefinition

/**
 * @author ilyas
 */

trait ScPackageContainer extends ScalaPsiElement {

  // Full-qualified name
  def fqn: String

  def packagings: Seq[ScPackaging]

  def typeDefs: Seq[ScTypeDefinition]

}