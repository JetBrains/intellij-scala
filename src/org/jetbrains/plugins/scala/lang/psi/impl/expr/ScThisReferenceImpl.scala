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
import types.result.{TypingContext, Failure, Success}
import api.base.ScConstructor
import com.intellij.psi.PsiElement
import api.toplevel.templates.ScTemplateBody

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScThisReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThisReference {
  override def toString: String = "ThisReference"

  protected override def innerType(ctx: TypingContext) = refTemplate match {
    case Some(td) => td.getType(ctx) map { refType =>
      td.selfType match {
        case Some(t) => Bounds.glb(refType, t)
        case None => refType
      }
    }
    case _ => Failure("Cannot infer type", Some(this))
  }

  def refTemplate = reference match {
    case Some(ref) => ref.resolve match {
      case td: ScTypeDefinition => Some(td)
      case _ => None
    }
    case None => {
      val encl = PsiTreeUtil.getContextOfType(this, classOf[ScTemplateBody], false)
      if (encl != null) Some(PsiTreeUtil.getContextOfType(encl, classOf[ScTemplateDefinition], false)) else None
    }
  }
}