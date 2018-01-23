package org.jetbrains.plugins.scala.lang.psi.api.statements

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiElement, PsiTypeParameter}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.{ConcurrentMapExt, PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
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

  implicit class PsiTypeParameterExt[T](val t: T) extends AnyVal {
    def nameAndId(implicit ev: NameAndId[T]): (String, Long) = ev.nameAndId(t)

    def typeParamId(implicit ev: NameAndId[T]): Long = ev.typeParamId(t)
  }

  trait NameAndId[-T] {
    def typeParamName(t: T): String

    def typeParamId(t: T): Long

    def nameAndId(t: T): (String, Long) = (typeParamName(t), typeParamId(t))
  }

  object NameAndId {
    implicit val psi: NameAndId[PsiTypeParameter] = new NameAndId[PsiTypeParameter] {
      override def typeParamName(t: PsiTypeParameter): String = t.name

      override def typeParamId(t: PsiTypeParameter): Long = t match {
        case sc: ScTypeParam => sc.typeParamId
        case psiTp =>
          Option(psiTp.getOwner)
            .flatMap(owner => Option(owner.containingClass))
            .map(cachedId)
            .getOrElse(-1L)
      }
    }

    implicit val typeParam: NameAndId[TypeParameter] = new NameAndId[TypeParameter] {
      override def typeParamName(t: TypeParameter): String = t.name
      override def typeParamId(t: TypeParameter): Long = psi.typeParamId(t.psiTypeParameter)
    }

    implicit val typeParamType: NameAndId[TypeParameterType] = new NameAndId[TypeParameterType] {
      override def typeParamName(t: TypeParameterType): String = t.name
      override def typeParamId(t: TypeParameterType): Long = psi.typeParamId(t.psiTypeParameter)
    }
  }

}
