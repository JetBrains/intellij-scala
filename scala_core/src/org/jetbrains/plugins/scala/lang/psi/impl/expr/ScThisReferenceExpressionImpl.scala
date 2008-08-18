package org.jetbrains.plugins.scala.lang.psi.impl.expr

import types.{Bounds, ScDesignatorType, ScCompoundType, Nothing}
import psi.ScalaPsiElementImpl
import api.expr._
import com.intellij.lang.ASTNode

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
        case Some(t) => Bounds.glb(des, t)
        case None => des
      }
    }
    case _ => Nothing
  }
}