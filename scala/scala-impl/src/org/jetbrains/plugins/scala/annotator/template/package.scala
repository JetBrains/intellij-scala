package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

package object template {

  private[template] def superRefs(definition: ScTemplateDefinition) =
    collectSuperRefs(definition)(_.extractClass)

  private[template] def collectSuperRefs[T](definition: ScTemplateDefinition)
                                           (extractor: ScType => Option[T]) =
    for {
      parents <- definition.physicalExtendsBlock.templateParents.toList
      typeElement <- parents.typeElements
      scType <- typeElement.`type`().toOption
      extracted <- extractor(scType)
    } yield (typeElement.getTextRange, extracted)

  private[template] def isMixable(clazz: PsiClass) = isInterface(clazz)()

  private[template] def isAbstract(clazz: PsiClass): Boolean = isInterface(clazz)(clazz.hasAbstractModifier)

  private[template] def kindOf(clazz: PsiClass, toLowerCase: Boolean = false) = {
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
