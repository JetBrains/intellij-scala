package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScRefineStatImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScRefineStat{
  override def toString: String = "RefinementStatement"
}