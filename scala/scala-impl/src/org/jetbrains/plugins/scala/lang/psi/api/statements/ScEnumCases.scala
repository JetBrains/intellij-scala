package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

trait ScEnumCases extends ScDeclaredElementsHolder with ScModifierListOwner {
  override def declaredElements: Seq[ScEnumCase]
}
