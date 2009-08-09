package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.psi.util.PsiTreeUtil
import api.toplevel.typedef.ScTypeDefinition
import types.{Bounds, ScDesignatorType, Nothing}
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

  def refClass = reference match {
    case Some(ref) => ref.resolve match {
      case td : ScTypeDefinition => Some(td)
      case _ => None
    }
    case None => {
      val encl = PsiTreeUtil.getContextOfType(this, classOf[ScTypeDefinition], false)
      if (encl != null) Some(encl) else None
    }
  }
}