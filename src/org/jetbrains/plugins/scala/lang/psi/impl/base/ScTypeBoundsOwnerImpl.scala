package org.jetbrains.plugins.scala.lang.psi.impl.base

import api.toplevel.ScTypeBoundsOwner
import lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import psi.types.{Nothing, Any}
import api.base.types.ScTypeElement
import com.intellij.lang.ASTNode

trait ScTypeBoundsOwnerImpl extends ScTypeBoundsOwner {
  //todo[CYCLIC]
  def lowerBound = {
    val tLower = findLastChildByType(ScalaTokenTypes.tLOWER_BOUND)
    if (tLower != null) {
      PsiTreeUtil.getNextSiblingOfType(tLower, classOf[ScTypeElement]) match {
        case null => Nothing
        case te => te.getType
      }
    } else Nothing
  }

  def upperBound = {
    val tUpper = findLastChildByType(ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      PsiTreeUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => Any
        case te => te.getType
      }
    } else Any
  }

  override def viewBound = {
    val tView = findLastChildByType(ScalaTokenTypes.tVIEW)
    if (tView != null) {
      PsiTreeUtil.getNextSiblingOfType(tView, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te.getType)
      }
    } else None
  }


  override def upperTypeElement: Option[ScTypeElement] = {
    val tUpper = findLastChildByType(ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      PsiTreeUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }

  override def lowerTypeElement: Option[ScTypeElement] = {
    val tLower = findLastChildByType(ScalaTokenTypes.tLOWER_BOUND)
    if (tLower != null) {
      PsiTreeUtil.getNextSiblingOfType(tLower, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }


  override def viewTypeElement: Option[ScTypeElement] = {
    val tView = findLastChildByType(ScalaTokenTypes.tVIEW)
    if (tView != null) {
      PsiTreeUtil.getNextSiblingOfType(tView, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }
}