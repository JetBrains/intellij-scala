package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId

trait ScEnumCasesBase extends ScDeclaredElementsHolderBase { this: ScEnumCases =>

  override def declaredElements: Seq[ScFieldId]
}