package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId

trait ScEnumCases extends ScDeclaredElementsHolder {

  override def declaredElements: Seq[ScFieldId]
}
