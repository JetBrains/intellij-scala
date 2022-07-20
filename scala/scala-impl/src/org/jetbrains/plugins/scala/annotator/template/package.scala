package org.jetbrains.plugins.scala
package annotator

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

// TODO move to annotator or to ScTemplateDefinition
package object template {

  def superRefs(definition: ScTemplateDefinition): Seq[(TextRange, PsiClass)] =
    collectSuperRefs(definition)(_.extractClass)

  def collectSuperRefs[T](definition: ScTemplateDefinition)
                         (extractor: ScType => Option[T]): Seq[(TextRange, T)] =
    for {
      parents     <- definition.extendsBlock.templateParents.toList
      typeElement <- parents.typeElements
      scType      <- typeElement.`type`().toOption
      extracted   <- extractor(scType)
    } yield (typeElement.getTextRange, extracted)

  def isMixable(clazz: PsiClass): Boolean = isInterface(clazz)()

  def isAbstract(clazz: PsiClass): Boolean = isInterface(clazz)(clazz.hasAbstractModifier)

  def kindOf(clazz: PsiClass, toLowerCase: Boolean = false): String = {
    val result = clazz match {
      case _: ScTrait => "Trait"
      case _: ScObject => "Object"
      case _ if clazz.isEnum => "Enum"
      case _ if clazz.isInterface => "Interface"
      case _ => "Class"
    }

    if (toLowerCase) result.toLowerCase else result
  }

  private[this] def isInterface(clazz: PsiClass)
                               (defaultValue: => Boolean = false) = clazz match {
    case _: ScTrait => true
    case _ if clazz.isInterface => !clazz.isAnnotationType
    case _ => defaultValue
  }
}
