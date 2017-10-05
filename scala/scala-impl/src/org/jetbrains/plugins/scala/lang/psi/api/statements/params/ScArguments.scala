package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

/**
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScArguments extends ScalaPsiElement {
  def getArgsCount: Int
}