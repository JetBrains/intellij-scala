package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScTypePatternBase extends ScalaPsiElementBase { this: ScTypePattern =>
  def typeElement: ScTypeElement = findChild[ScTypeElement].get
}