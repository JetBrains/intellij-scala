package org.jetbrains.plugins.scala.lang.psi.impl.base

import api.toplevel.ScTypeBoundsOwner
import lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import psi.types.{Nothing, Any}
import api.base.types.ScTypeElement
import com.intellij.lang.ASTNode

class ScTypeBoundsOwnerImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeBoundsOwner {
  //todo[CYCLIC]
  def lowerBound = {
    val tLower = findChildByType(ScalaTokenTypes.tLOWER_BOUND)
    if (tLower != null) {
      PsiTreeUtil.getNextSiblingOfType(tLower, classOf[ScTypeElement]) match {
        case null => Nothing
        case te => te.getType
      }
    } else Nothing
  }

  def upperBound = {
    val tUpper = findChildByType(ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      PsiTreeUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => Any
        case te => te.getType
      }
    } else Any
  }

  override def viewBound = {
    val tView = findChildByType(ScalaTokenTypes.tVIEW)
    if (tView != null) {
      PsiTreeUtil.getNextSiblingOfType(tView, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te.getType)
      }
    } else None
  }
}