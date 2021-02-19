package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

trait ScTypedDeclarationBase extends ScDeclarationBase with Typeable { this: ScTypedDeclaration =>
  override def declaredElements: Seq[ScTypedDefinition]
}