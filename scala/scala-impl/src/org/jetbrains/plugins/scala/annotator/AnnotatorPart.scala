package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}

import scala.reflect.ClassTag

/**
  * Pavel Fatin
  */
abstract class AnnotatorPart[T <: ScalaPsiElement : ClassTag] {

  def annotate(element: T, holder: AnnotationHolder, typeAware: Boolean)

  //TODO move to PsiClass extensions

  protected def kindOf(entity: PsiClass): String = entity match {
    case _: ScTrait => "Trait"
    case _: ScObject => "Object"
    case c: PsiClass if c.isEnum => "Enum"
    case c: PsiClass if c.isInterface => "Interface"
    case _ => "Class"
  }

  protected def isMixable(entity: PsiClass): Boolean = entity match {
    case _: ScTrait => true
    case c: PsiClass if c.isInterface => !c.isAnnotationType
    case _ => false
  }

  protected def isAbstract(entity: PsiClass): Boolean = entity match {
    case _: ScTrait => true
    case c: PsiClass if c.isInterface => !c.isAnnotationType
    case c: PsiClass if c.hasAbstractModifier => true
    case _ => false
  }
}
