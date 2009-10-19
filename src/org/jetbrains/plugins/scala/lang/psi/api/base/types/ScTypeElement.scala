package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import result.{TypingContext, TypingContextOwner}
import toplevel.ScNamedElement
import com.intellij.psi.util.PsiModificationTracker

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