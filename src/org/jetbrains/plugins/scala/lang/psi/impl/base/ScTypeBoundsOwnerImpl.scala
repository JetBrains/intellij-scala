package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import api.toplevel.ScTypeBoundsOwner
import lexer.ScalaTokenTypes
import api.base.types.ScTypeElement
import psi.types.{ScType, Nothing, Any}
import psi.types.result.{TypingContext, Success, TypeResult}
import com.intellij.psi.util.{PsiUtil, PsiTreeUtil}
import com.intellij.psi.PsiWhiteSpace

trait ScTypeBoundsOwnerImpl extends ScTypeBoundsOwner {
  //todo[CYCLIC]
  def lowerBound: TypeResult[ScType] = wrapWith(lowerTypeElement, Nothing) flatMap ( _.getType(TypingContext.empty) )

  def upperBound: TypeResult[ScType] = wrapWith(upperTypeElement, Any) flatMap ( _.getType(TypingContext.empty) )

  override def viewBound: List[ScType] = viewTypeElement flatMap (_.getType(TypingContext.empty).toOption.toList)

  override def contextBound: List[ScType] = contextBoundTypeElement flatMap (_.getType(TypingContext.empty).toOption.toList)

  override def upperTypeElement: Option[ScTypeElement] = {
    val tUpper = findLastChildByType(ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      ScalaPsiUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }

  override def lowerTypeElement: Option[ScTypeElement] = {
    val tLower = findLastChildByType(ScalaTokenTypes.tLOWER_BOUND)
    if (tLower != null) {
      ScalaPsiUtil.getNextSiblingOfType(tLower, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }


  override def viewTypeElement: List[ScTypeElement] = {
    for {v <- findChildrenByType(ScalaTokenTypes.tVIEW)
        t <- {
          val e = ScalaPsiUtil.getNextSiblingOfType(v, classOf[ScTypeElement])
          Option(e)}.toList
    } yield t
  }

  override def contextBoundTypeElement: List[ScTypeElement] = {
    for {v <- findChildrenByType(ScalaTokenTypes.tCOLON)
        t <- Option(ScalaPsiUtil.getNextSiblingOfType(v, classOf[ScTypeElement])).toList
    } yield t
  }

  override def removeImplicitBounds() {
    var node = getNode.getFirstChildNode
    while (node != null && !Set(ScalaTokenTypes.tCOLON, ScalaTokenTypes.tVIEW)(node.getElementType)) {
      node = node.getTreeNext
    }
    if (node == null) return
    node.getPsi.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _ =>
    }
    node.getTreeParent.removeRange(node, null)
  }
}