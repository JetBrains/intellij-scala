package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScPatternArgumentList extends ScArguments {

  def patterns: Seq[ScPattern]

}