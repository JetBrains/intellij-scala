package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.psi.util.PsiTreeUtil
import types.{Bounds, ScDesignatorType, Nothing}
import psi.ScalaPsiElementImpl
import api.expr._
import com.intellij.lang.ASTNode
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScThisReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThisReference {
  override def toString: String = "ThisReference"

  override def getType = refTemplate match {
    case Some(td) => {
      val refType = td.getType
      td.selfType match {
        case Some(t) => Bounds.glb(refType, t)
        case None => refType
      }
    }
    case _ => Nothing
  }

  def refTemplate = reference match {
    case Some(ref) => ref.resolve match {
      case td : ScTypeDefinition => Some(td)
      case _ => None
    }
    case None => {
      val encl = PsiTreeUtil.getContextOfType(this, classOf[ScTemplateDefinition], false)
      if (encl != null) Some(encl) else None
    }
  }
}