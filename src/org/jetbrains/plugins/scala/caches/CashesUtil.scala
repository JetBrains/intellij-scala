package org.jetbrains.plugins.scala.caches


import com.intellij.openapi.util.Key
import lang.psi.api.base.types.ScTypeInferenceResult
import com.intellij.psi.{PsiElement, PsiManager}

import com.intellij.psi.util.{CachedValue, CachedValueProvider}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */
//todo: copy from TypeDefinitionMembers, rewrite or remove duplicates
object CashesUtil {
  val ALIASED_KEY: Key[CachedValue[ScTypeInferenceResult]] = Key.create("alised type key")
  val SCRIPT_KEY: Key[CachedValue[java.lang.Boolean]] = Key.create("is script key")

  def get[Dom <: PsiElement, T](e: Dom, key: Key[CachedValue[T]], provider: => CachedValueProvider[T]): T = {
    var computed: CachedValue[T] = e.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(e.getProject).getCachedValuesManager
      computed = manager.createCachedValue(provider, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[Dom, T](e: Dom, builder: Dom => T)(dependencyItem: Object) extends CachedValueProvider[T] {
    def compute() = new CachedValueProvider.Result(builder(e), dependencyItem)
  }
}