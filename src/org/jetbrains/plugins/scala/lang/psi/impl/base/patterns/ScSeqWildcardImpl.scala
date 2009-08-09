package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode


/** 
 * @author ilyas
 */

class ScSeqWildcardImpl (node: ASTNode) extends ScalaPsiElementImpl(node) with ScSeqWildcard {

  override def toString: String = "Sequence Wildcard"

}