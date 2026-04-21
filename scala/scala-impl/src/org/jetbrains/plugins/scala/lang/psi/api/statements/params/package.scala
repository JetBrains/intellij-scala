package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiTypeParameter}
import com.intellij.util.containers.{ConcurrentLongObjectMap, ContainerUtil}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiElementExt, PsiNamedElementExt, StubBasedExt}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.TypeParameterDebugRendering

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.language.implicitConversions

package object params {
  private val typeParameterCounter = new AtomicLong(0)
  private val reusableIdMap = new ConcurrentHashMap[String, Long]()

  private val paramToIdMap = ContainerUtil.createConcurrentWeakMap[PsiNamedElement, Long]()

  //for better debugging, cleared by ScalaPsiManager on every change
  val idToName: ConcurrentLongObjectMap[String] =
    ConcurrentCollectionFactory.createConcurrentLongObjectMap()

  //never cleared
  private val reusableIdToName: ConcurrentLongObjectMap[String] =
    ConcurrentCollectionFactory.createConcurrentLongObjectMap()

  def typeParamName(id: Long): String = {
    val name =
      idToName
        .get(id)
        .toOption
        .getOrElse(reusableIdToName.get(id)).toOption

    name match {
      case Some(n) => TypeParameterDebugRendering.withTypeParamId(n, id)
      case None    => id.toString
    }
  }

  private val nameBasedIdBaseline = Long.MaxValue / 2

  private def elementQual(element: ScalaPsiElement): String =
    element match {
      case t: ScTypeParam => elementQual(t.owner) + "#" + t.name
      case c: PsiClass    => c.qualifiedName
      case f: ScFunction  =>
        val indexInParent = f.withGreenStub(
          stub => stub.getParentStub.getChildrenStubs.indexOf(stub),
          () => 0
        )
        elementQual(f.containingClass) + ".." + indexInParent
      case _              => ""
    }

  def freshTypeParamId(element: PsiNamedElement): Long = {
    val id = typeParameterCounter.getAndIncrement()
    idToName.put(id, element.name)
    id
  }

  def reusableId(typeParameter: ScTypeParam): Long = typeParameter.containingFile match {
    case Some(file: ScalaFile) if file.isCompiled =>
      val qualifier = elementQual(typeParameter)
      val id = reusableIdMap.computeIfAbsent(qualifier, _ => freshTypeParamId(typeParameter))
      reusableIdToName.put(id, typeParameter.name)
      id
    case _ => freshTypeParamId(typeParameter)
  }

  private def cachedId(element: PsiNamedElement): Long = paramToIdMap.computeIfAbsent(element, freshTypeParamId(_))

  implicit class TypeParamIdOwner[T](private val t: T) extends AnyVal {
    def typeParamId(implicit ev: TypeParamId[T]): Long = ev.typeParamId(t)
    def typeParamName(implicit ev: TypeParamId[T]): Option[String] = ev.typeParamName(t)
  }

  trait TypeParamId[-T] {
    def typeParamId(t: T): Long
    def typeParamName(t: T): Option[String]
  }

  object TypeParamId {
    implicit val psi: TypeParamId[PsiTypeParameter] = new TypeParamId[PsiTypeParameter] {
      override def typeParamId(t: PsiTypeParameter): Long = t match {
        case sc: ScTypeParam => sc.typeParamId
        case null            => -1
        case p               => cachedId(p)
      }

      override def typeParamName(t: PsiTypeParameter): Option[String] = Option(t).map(_.name)
    }

    implicit val typeParam: TypeParamId[TypeParameter] = new TypeParamId[TypeParameter] {
      override def typeParamId(t: TypeParameter): Long =
        psi.typeParamId(t.psiTypeParameter)

      override def typeParamName(t: TypeParameter): Option[String] =
        Option(t.name)
    }

    implicit val typeParamType: TypeParamId[TypeParameterType] = new TypeParamId[TypeParameterType] {
      override def typeParamId(t: TypeParameterType): Long =
        psi.typeParamId(t.psiTypeParameter)

      override def typeParamName(t: TypeParameterType): Option[String] =
        Option(t.name)
    }

    implicit val long: TypeParamId[Long] = new TypeParamId[Long] {
      override def typeParamId(t: Long): Long = t

      override def typeParamName(t: Long): Option[String] = None
    }

    //I'd rather avoid implicit usages of this one
    val nameBased: TypeParamId[String] = new TypeParamId[String] {
      override def typeParamId(name: String): Long =
        nameBasedIdBaseline + name.hashCode

      override def typeParamName(name: String): Option[String] =
        Option(name)
    }
  }

}
