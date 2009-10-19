package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.types.{ScTypeInferenceResult, ScTypeElement}
import caches.CachesUtil
import com.intellij.psi.util.{PsiModificationTracker, CachedValueProvider, CachedValue}
import com.intellij.psi.{PsiManager, PsiElement}
import stubs.ScTypeAliasStub
import toplevel.ScNamedElement
import types.result.{TypeResult, Failure, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  def aliasedTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def aliasedType(ctx: TypingContext): TypeResult[ScType] = {
    if (ctx.visited.contains(this)) {
      Failure(ScalaBundle.message("circular.dependency.detected", getName), Some(this))
    } else {
      val stub = this.asInstanceOf[ScalaStubBasedElementImpl[_ <: PsiElement]].getStub
      if (stub != null) {
        stub.asInstanceOf[ScTypeAliasStub].getTypeElement.getType(ctx(this))
      } else 
        aliasedTypeElement.getType(ctx(this))
    }
  }

  def aliasedType: ScTypeInferenceResult = CachesUtil.get(
      this, CachesUtil.ALIASED_KEY,
      new CachesUtil.MyProvider(this, {ta: ScTypeAliasDefinition => ta.aliasedType(TypingContext.empty)})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )

  def lowerBound: TypeResult[ScType] = aliasedType(TypingContext.empty)
  def upperBound: TypeResult[ScType] = aliasedType(TypingContext.empty)
}