package org.jetbrains.plugins.scala.lang.psi.impl.expr

import types.{ScDesignatorType, ScCompoundType, Nothing}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScThisReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThisReference {
  override def toString: String = "ThisReference"

  override def getType = refClass match {
    case Some(td) => {
      val des = new ScDesignatorType(td)
      td.selfType match {
        case Some(t) => new ScCompoundType(Array(des, t), Seq.empty, Seq.empty)
        case None => des
      }
    }
    case _ => Nothing
  }
}