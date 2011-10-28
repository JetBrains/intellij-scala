package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.psi.util.PsiTreeUtil
import psi.ScalaPsiElementImpl
import api.expr._
import com.intellij.lang.ASTNode
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import api.base.ScConstructor
import com.intellij.psi.PsiElement
import api.toplevel.templates.ScTemplateBody
import types._
import result.{TypeResult, TypingContext, Failure, Success}

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScThisReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThisReference {
  override def toString: String = "ThisReference"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = refTemplate match {
    case Some(td) => {
      lazy val selfTypeOfClass: ScType = td.getTypeWithProjections(TypingContext.empty, true).map(tp => {
        td.selfType match {
          case Some(selfType) => Bounds.glb(tp, selfType)
          case _ => tp
        }
      }).getOrElse(return Failure("No clazz type found", Some(this)))

      // SLS 6.5:  If the expression’s expected type is a stable type,
      // or C .this occurs as the prefix of a selection, its type is C.this.type,
      // otherwise it is the self type of class C .
      getContext match {
        case r: ScReferenceExpression if r.qualifier.exists(_ == this) =>
          Success(ScThisType(td), Some(this))
        case _ => expectedType() match {
          case Some(t) if t.isStable =>
            Success(ScThisType(td), Some(this))
          case _ => 
            Success(selfTypeOfClass, Some(this))
        }
      }
    }
    case _ => Failure("Cannot infer type", Some(this))
  }

  def refTemplate: Option[ScTemplateDefinition] = reference match {
    case Some(ref) => ref.resolve match {
      case td: ScTypeDefinition if PsiTreeUtil.isContextAncestor(td, ref, false) => Some(td)
      case _ => None
    }
    case None => {
      val encl = PsiTreeUtil.getContextOfType(this, false, classOf[ScTemplateBody])
      if (encl != null) Some(PsiTreeUtil.getContextOfType(encl, false, classOf[ScTemplateDefinition])) else None
    }
  }
}