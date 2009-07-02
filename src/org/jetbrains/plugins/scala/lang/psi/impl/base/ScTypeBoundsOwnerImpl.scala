package org.jetbrains.plugins.scala.lang.psi.impl.base

import api.toplevel.ScTypeBoundsOwner
import lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import api.base.types.ScTypeElement
import psi.types.{ScType, Nothing, Any}

trait ScTypeBoundsOwnerImpl extends ScTypeBoundsOwner {
  //todo[CYCLIC]
  def lowerBound: ScType = {
    lowerTypeElement match {
      case Some(te) => te.cachedType
      case None => Nothing
    }
  }

  def upperBound: ScType = {
    upperTypeElement match {
      case Some(te) => te.cachedType
      case None => Any
    }
  }

  override def viewBound: Option[ScType] = {
    viewTypeElement match {
      case Some(te) => Some(te.cachedType)
      case None => None
    }
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