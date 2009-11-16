package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import result.{TypeResult, TypingContext, TypingContextOwner}
import com.intellij.openapi.progress.ProgressManager

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypeElement extends ScalaPsiElement with TypingContextOwner {

  @volatile
  private var elementType: TypeResult[ScType] = null

  @volatile
  private var modCount: Long = 0

  def getType(ctx: TypingContext): TypeResult[ScType] = {
    if (ctx.visited.size != 0) return innerType(ctx)
    var tp = elementType
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && modCount == curModCount) {
      return tp
    }
    ProgressManager.checkCanceled
    tp = innerType(ctx)
    elementType = tp
    modCount = curModCount
    return tp
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType]

  def calcType: ScType = getType(TypingContext.empty).getOrElse(Any)
}