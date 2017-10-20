package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTuplePattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "TuplePattern"

  override def subpatterns: Seq[ScPattern] =  patternList match {
    case Some(l) => l.patterns
    case None => Seq.empty
  }
}