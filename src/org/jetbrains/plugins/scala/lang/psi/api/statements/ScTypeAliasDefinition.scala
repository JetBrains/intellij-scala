package org.jetbrains.plugins.scala.lang.psi.api.statements

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.types.{ScTypeInferenceResult, ScTypeElement}
import com.intellij.openapi.util.Key
import com.intellij.psi.util.{PsiModificationTracker, CachedValueProvider, CachedValue}
import com.intellij.psi.{PsiManager, PsiElement}



import impl.toplevel.typedef.TypeDefinitionMembers
import toplevel.ScNamedElement
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  def aliasedTypeElement = findChildByClass(classOf[ScTypeElement])

  //todo make cached!
  def aliasedType(visited: Set[ScNamedElement]): ScTypeInferenceResult = {
    if (visited.contains(this)) {
      ScTypeInferenceResult(types.Nothing, true, Some(this))
    } else aliasedTypeElement.getType(visited + this)
  }

  def aliasedType: ScTypeInferenceResult = ScTypeAliasDefinitionCaches.get(
      this, ScTypeAliasDefinitionCaches.key,
      new ScTypeAliasDefinitionCaches.MyProvider(this, {ta: ScTypeAliasDefinition => ta.aliasedType(Set[ScNamedElement]())})
    )

  def lowerBound = aliasedType(Set[ScNamedElement]())
  def upperBound = aliasedType(Set[ScNamedElement]())
}

//todo: copy from TypeDefinitionMembers, rewrite or remove dublicates
private object ScTypeAliasDefinitionCaches {
  val key: Key[CachedValue[ScTypeInferenceResult]] = Key.create("alised type key")

  def get[Dom <: PsiElement, T](e: Dom, key: Key[CachedValue[T]], provider: => CachedValueProvider[T]): T = {
    var computed: CachedValue[T] = e.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(e.getProject).getCachedValuesManager
      computed = manager.createCachedValue(provider, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[Dom, T](e: Dom, builder: Dom => T) extends CachedValueProvider[T] {
    def compute() = new CachedValueProvider.Result(builder(e),
      PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT)
  }
}