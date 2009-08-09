package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTuplePattern{
  override def toString: String = "TuplePattern"

  override def subpatterns =  patternList match {
    case Some(l) => l.patterns
    case None => Seq.empty
  }
}