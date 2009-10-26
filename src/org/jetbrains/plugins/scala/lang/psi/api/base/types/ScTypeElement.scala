package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import result.{TypeResult, TypingContext, TypingContextOwner}
import com.intellij.psi.util.PsiModificationTracker

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypeElement extends ScalaPsiElement with TypingContextOwner {

  def cachedType: TypeResult[ScType] = {
    CachesUtil.get(
      this, CachesUtil.TYPE_KEY,
      new CachesUtil.MyProvider(this, {ic: ScTypeElement => ic.getType(TypingContext.empty)})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )
  }

  def calcType: ScType = getType(TypingContext.empty).getOrElse(Any)
}