package org.jetbrains.plugins.scala.lang.psi.api.statements

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiTypeParameter}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.{ConcurrentMapExt, ObjectExt, PsiClassExt, PsiElementExt, PsiNamedElementExt}
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
  private val reusableIdMap = ContainerUtil.newConcurrentMap[String, Long]()

  private val paramToIdMap = ContainerUtil.createConcurrentWeakMap[PsiNamedElement, Long]()
  //for better debugging
  private val idToParamMap = ContainerUtil.createWeakValueMap[Long, PsiNamedElement]()

  def typeParamName(id: Long): String =
    idToParamMap.get(id).toOption.fold("unknown id")(_.name)

  private val nameBasedIdBaseline = Long.MaxValue / 2

  private def elementQual(element: ScalaPsiElement): String = {
    element match {
      case t: ScTypeParam => elementQual(t.owner) + "#" + t.name
      case c: PsiClass => c.qualifiedName
      case f: ScFunction => elementQual(f.containingClass) + ".." + f.name
      case _ => ""
    }
  }

  def freshTypeParamId(element: PsiNamedElement): Long = {
    val id = typeParameterCounter.getAndIncrement()
    idToParamMap.put(id, element)
    id
  }

  def reusableId(typeParameter: ScTypeParam): Long = typeParameter.containingFile match {
    case Some(file: ScalaFile) if file.isCompiled =>
      val qualifier = elementQual(typeParameter)
      reusableIdMap.computeIfAbsent(qualifier, _ => freshTypeParamId(typeParameter))
    case _ => freshTypeParamId(typeParameter)
  }

  private def cachedId(element: PsiNamedElement, name: String): Long = paramToIdMap.computeIfAbsent(element, freshTypeParamId(_))

  implicit class TypeParamIdOwner[T](val t: T) extends AnyVal {
    def typeParamId(implicit ev: TypeParamId[T]): Long = ev.typeParamId(t)
  }

  trait TypeParamId[-T] {
    def typeParamId(t: T): Long
  }

  object TypeParamId {
    implicit val psi: TypeParamId[PsiTypeParameter] = psiTypeParameter => psiTypeParameter match {
      case sc: ScTypeParam => sc.typeParamId
      case null => -1
      case p => cachedId(p, p.name)
    }

    implicit val typeParam: TypeParamId[TypeParameter] = t => psi.typeParamId(t.psiTypeParameter)

    implicit val typeParamType: TypeParamId[TypeParameterType] = t => psi.typeParamId(t.psiTypeParameter)

    implicit val long: TypeParamId[Long] = identity(_)

    //I'd rather avoid implicit usages of this one
    val nameBased: TypeParamId[String] = name => nameBasedIdBaseline + name.hashCode
  }

}
