package org.jetbrains.plugins.scala.lang.psi.api.statements
package params

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScArguments extends ScalaPsiElement {
  def getArgsCount: Int
}