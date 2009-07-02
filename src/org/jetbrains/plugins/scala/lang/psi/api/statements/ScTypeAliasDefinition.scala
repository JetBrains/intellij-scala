package org.jetbrains.plugins.scala.lang.psi.api.statements

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.types.{ScTypeInferenceResult, ScTypeElement}
import caches.CachesUtil
import com.intellij.psi.util.{PsiModificationTracker, CachedValueProvider, CachedValue}
import com.intellij.psi.{PsiManager, PsiElement}
import stubs.ScTypeAliasStub
import toplevel.ScNamedElement
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  def aliasedTypeElement = findChildByClass(classOf[ScTypeElement])

  def aliasedType(visited: Set[ScNamedElement]): ScTypeInferenceResult = {
    if (visited.contains(this)) {
      ScTypeInferenceResult(types.Nothing, true, Some(this))
    } else {
      val stub = this.asInstanceOf[ScalaStubBasedElementImpl[_]].getStub
      if (stub != null) {
        stub.asInstanceOf[ScTypeAliasStub].getTypeElement.getType(visited + this)
      } else 
        aliasedTypeElement.getType(visited + this)
    }
  }

  def aliasedType: ScTypeInferenceResult = CachesUtil.get(
      this, CachesUtil.ALIASED_KEY,
      new CachesUtil.MyProvider(this, {ta: ScTypeAliasDefinition => ta.aliasedType(Set[ScNamedElement]())})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )

  def lowerBound: ScType = aliasedType(Set[ScNamedElement]())
  def upperBound: ScType = aliasedType(Set[ScNamedElement]())
}