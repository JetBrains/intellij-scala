package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.openapi.progress.ProgressManager
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker
import result.{Failure, TypeResult, TypingContext, TypingContextOwner}

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypeElement extends ScalaPsiElement with TypingContextOwner {
  def getType(ctx: TypingContext): TypeResult[ScType] = {
    CachesUtil.getWithRecursionPreventing(this, CachesUtil.TYPE_ELEMENT_TYPE_KEY,
      new CachesUtil.MyProvider[ScTypeElement, TypeResult[ScType]](
        this, elem => innerType(ctx)
      )(PsiModificationTracker.MODIFICATION_COUNT), Failure("Recursive type of type element", Some(this)))
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType]

  def calcType: ScType = getType(TypingContext.empty).getOrAny
}