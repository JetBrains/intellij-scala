package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import _root_.scala.collection.immutable.HashSet
import caches.CachesUtil
import expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.psi._
import result.{TypingContext, TypeResult, TypingContextOwner}
import toplevel.ScNamedElement
import util.PsiModificationTracker
import collection.Set

/**
* @author Alexander Podkhalyuzin
* Date: 14.04.2008
*/

trait ScTypeElement extends ScalaPsiElement with TypingContextOwner {
  def cachedType: ScTypeInferenceResult = {
    CachesUtil.get(
      this, CachesUtil.TYPE_KEY,
      new CachesUtil.MyProvider(this, {ic: ScTypeElement => ic.calcType})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )
  }

  def calcType: ScType = getType(TypingContext.empty).resType
}

case class ScTypeInferenceResult(resType: ScType, isCyclic: Boolean, cycleStart: Option[ScNamedElement])