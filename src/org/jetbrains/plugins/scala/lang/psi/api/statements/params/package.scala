package org.jetbrains.plugins.scala.lang.psi.api.statements

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiElement, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticTypeParameter
import org.jetbrains.plugins.scala.lang.psi.light.scala.ScLightTypeParam

import scala.collection.mutable

/**
  * @author adkozlov
  */
package object params {
  private val typeParameterCounter = new AtomicLong(0)
  private val typeParameterIdKey: Key[java.lang.Long] = Key.create("type.parameter.id")
  private val idMap = mutable.HashMap.empty[String, Long]
  var reuseIdBeteewenClasses: Boolean = true

  private def elementQual(element: PsiElement): String = {
    element match {
      case t: ScTypeParam => elementQual(t.owner) + "#" + t.name
      case c: PsiClass => c.qualifiedName
      case f: ScFunction => elementQual(f.containingClass) + ".." + f.name
      case _ => ""
    }
  }

  private def cachedId(element: PsiElement): Long = {
    val cached = element.getUserData(typeParameterIdKey)
    if (cached == null) {
      element.synchronized {
        val secondTry = element.getUserData(typeParameterIdKey)
        if (secondTry == null) {
          def cacheId(eval: => Long): Long = {
            val next = eval
            element.putUserData(typeParameterIdKey, java.lang.Long.valueOf(next))
            next
          }
          def getAndIncrementId() = typeParameterCounter.getAndIncrement()

          if (reuseIdBeteewenClasses) {
            element.containingFile match {
              case Some(file: ScalaFile) if file.isCompiled =>
                val qualifier = elementQual(element)
                idMap.synchronized {
                  cacheId { idMap.getOrElseUpdate(qualifier, getAndIncrementId()) }
                }
              case _ => cacheId { getAndIncrementId() }
            }
          } else cacheId { getAndIncrementId() }
        } else secondTry.longValue()
      }
    } else cached.longValue()
  }

  implicit class PsiTypeParameterExt(val typeParameter: PsiTypeParameter) extends AnyVal {
    def nameAndId: (String, Long) = (typeParameter.name, typeParameter match {
      case s: ScSyntheticTypeParameter => -1L
      case sc: ScLightTypeParam => sc.tParam.id
      case sc: ScTypeParam => cachedId(sc)
      case psiTp =>
        Option(psiTp.getOwner)
          .flatMap(owner => Option(owner.containingClass))
          .map(cachedId)
          .getOrElse(-1L)
    })

    def id: Long = {
      val (_, id) = nameAndId
      id
    }
  }
}
