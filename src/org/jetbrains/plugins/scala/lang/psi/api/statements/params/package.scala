package org.jetbrains.plugins.scala.lang.psi.api.statements

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiElement, PsiTypeParameter}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.{ConcurrentMapExt, PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.UserDataHolderExt

import scala.language.implicitConversions

/**
  * @author adkozlov
  */
package object params {
  private val typeParameterCounter = new AtomicLong(0)
  private val typeParameterIdKey: Key[java.lang.Long] = Key.create("type.parameter.id")
  private val idMap = ContainerUtil.newConcurrentMap[String, Long]()

  private def elementQual(element: ScalaPsiElement): String = {
    element match {
      case t: ScTypeParam => elementQual(t.owner) + "#" + t.name
      case c: PsiClass => c.qualifiedName
      case f: ScFunction => elementQual(f.containingClass) + ".." + f.name
      case _ => ""
    }
  }

  def freshTypeParamId(): Long = typeParameterCounter.getAndIncrement()

  def reusableId(typeParameter: ScTypeParam): Long = typeParameter.containingFile match {
    case Some(file: ScalaFile) if file.isCompiled =>
      val qualifier = elementQual(typeParameter)
      idMap.atomicGetOrElseUpdate(qualifier, freshTypeParamId())
    case _ => freshTypeParamId()
  }

  private def cachedId(element: PsiElement): Long = element.getOrUpdateUserData(typeParameterIdKey, Long.box(freshTypeParamId()))

  implicit class PsiTypeParameterExt(val typeParameter: PsiTypeParameter) extends AnyVal {
    def nameAndId: (String, Long) = (typeParameter.name, typeParamId)

    def typeParamId: Long = typeParameter match {
      case sc: ScTypeParam => sc.typeParamId
      case psiTp =>
        Option(psiTp.getOwner)
          .flatMap(owner => Option(owner.containingClass))
          .map(cachedId)
          .getOrElse(-1L)
    }
  }
}
