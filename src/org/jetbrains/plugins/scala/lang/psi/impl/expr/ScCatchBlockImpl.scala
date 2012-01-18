package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
 * Author: Alexander Podkhalyuzin
 * Date: 06.03.2008
 */
class ScCatchBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCatchBlock {
  override def toString: String = "CatchBlock"
}