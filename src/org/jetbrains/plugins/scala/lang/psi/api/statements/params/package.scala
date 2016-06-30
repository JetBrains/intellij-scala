package org.jetbrains.plugins.scala.lang.psi.api.statements

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElement, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions.{PsiMemberExt, PsiNamedElementExt}

/**
  * @author adkozlov
  */
package object params {

  private val typeParameterCounter = new AtomicLong(0)
  private val typeParameterIdKey: Key[java.lang.Long] = Key.create("type.parameter.id")

  private def cachedId(element: PsiElement): Long = {
    val cached = element.getUserData(typeParameterIdKey)
    if (cached == null) {
      val next = typeParameterCounter.getAndIncrement()
      element.putUserData(typeParameterIdKey, java.lang.Long.valueOf(next))
      next
    }
    else cached.longValue()
  }

  implicit class PsiTypeParameterExt(val typeParameter: PsiTypeParameter) extends AnyVal {
    def nameAndId: (String, Long) = (typeParameter.name, typeParameter match {
      case sc: ScTypeParam => sc.getPsiElementId
      case psiTp =>
        Option(psiTp.getOwner)
          .flatMap(owner => Option(owner.containingClass))
          .map(cachedId)
          .getOrElse(-1L)
    })

    def id: Long = cachedId(typeParameter)
  }
}
