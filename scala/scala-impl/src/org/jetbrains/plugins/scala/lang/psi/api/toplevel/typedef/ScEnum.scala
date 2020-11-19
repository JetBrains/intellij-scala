package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase

trait ScEnum extends ScConstructorOwner {
  def cases: Seq[ScEnumCase]

  def syntheticClass: Option[ScTypeDefinition]
}
