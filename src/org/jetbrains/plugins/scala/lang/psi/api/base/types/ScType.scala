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
import toplevel.ScNamedElement
import util.PsiModificationTracker
import collection.Set

/**
* @author Alexander Podkhalyuzin
* Date: 14.04.2008
*/

trait ScTypeElement extends ScalaPsiElement {
  def cachedType: ScTypeInferenceResult = {
    CachesUtil.get(
      this, CachesUtil.TYPE_KEY,
      new CachesUtil.MyProvider(this, {ic: ScTypeElement => ic.getType(HashSet[ScNamedElement]())})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )
  }

  def getType(implicit visited: collection.Set[ScNamedElement]) : ScTypeInferenceResult = ScTypeInferenceResult(Nothing, false, None)

  def calcType: ScType = getType(HashSet[ScNamedElement]()).resType
}

case class ScTypeInferenceResult(resType: ScType, isCyclic: Boolean, cycleStart: Option[ScNamedElement])