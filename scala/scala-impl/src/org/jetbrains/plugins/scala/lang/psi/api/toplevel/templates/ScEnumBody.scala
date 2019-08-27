package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase

trait ScEnumBody extends ScTemplateBody {
  def cases: Seq[ScEnumCase]
}