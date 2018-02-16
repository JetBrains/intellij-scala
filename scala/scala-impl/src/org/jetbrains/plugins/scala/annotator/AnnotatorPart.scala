package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.reflect.ClassTag

/**
 * Pavel Fatin
 */

abstract class AnnotatorPart[T <: ScalaPsiElement : ClassTag] {

  def annotate(element: T, holder: AnnotationHolder, typeAware: Boolean)

  //TODO move to PsiClass extensions

  protected def kindOf(entity: PsiClass) = entity match {
    case _: ScTrait => "Trait"
    case _: ScObject => "Object"
    case c: PsiClass if c.isEnum => "Enum"
    case c: PsiClass if c.isInterface => "Interface"
    case _ => "Class"
  }

  protected def isMixable(entity: PsiClass) = entity match {
    case _: ScTrait => true
    case c: PsiClass if c.isInterface => !c.isAnnotationType
    case _ => false
  }

  protected def isAbstract(entity: PsiClass) = entity match {
    case _: ScTrait => true
    case c: PsiClass if c.isInterface => !c.isAnnotationType
    case c: PsiClass if c.hasAbstractModifier => true
    case _ => false
  }
}

object AnnotatorPart {

  private def collectSuperRefs[T](td: ScTemplateDefinition, extractor: ScType => Option[T]) = {
    val superTypeElements = td.physicalExtendsBlock.templateParents.toSeq.flatMap(_.typeElements)
    for {
      typeElem <- superTypeElements
      tp <- typeElem.`type`().toOption
    } yield {
      (typeElem, extractor(tp))
    }
  }

  def superRefs(td: ScTemplateDefinition): Seq[(ScTypeElement, Option[PsiClass])] = {
    collectSuperRefs(td, _.extractClass)
  }

  def superRefsWithSubst(td: ScTemplateDefinition): Seq[(ScTypeElement, Option[(PsiClass, ScSubstitutor)])] = {
    collectSuperRefs(td, _.extractClassType)
  }

}