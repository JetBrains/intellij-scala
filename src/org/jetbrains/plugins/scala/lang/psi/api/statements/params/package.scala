package org.jetbrains.plugins.scala.lang.psi.api.statements

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt

/**
  * @author adkozlov
  */
package object params {

  private val typeParameterCounter = new AtomicLong(0)
  private val typeParameterIdKey: Key[java.lang.Long] = Key.create("type.parameter.id")

  implicit class PsiTypeParameterExt(val typeParameter: PsiTypeParameter) extends AnyVal {
    def nameAndId: (String, Long) = (typeParameter.name, id)

    def id: Long = {
      val cached = typeParameter.getUserData(typeParameterIdKey)
      if (cached == null) {
        val next = typeParameterCounter.getAndIncrement()
        typeParameter.putUserData(typeParameterIdKey, java.lang.Long.valueOf(next))
        next
      }
      else cached.longValue()
    }
  }

}
