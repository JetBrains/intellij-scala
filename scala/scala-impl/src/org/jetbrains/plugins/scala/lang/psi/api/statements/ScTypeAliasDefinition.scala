package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Invariant, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

trait ScTypeAliasDefinition extends ScTypeAlias {
  override def isDefinition: Boolean = true

  def aliasedTypeElement: Option[ScTypeElement]

  @CachedInUserData(this, BlockModificationTracker(this))
  def aliasedType: TypeResult =
    aliasedTypeElement.map {
      _.`type`()
    }.getOrElse(Failure(ScalaBundle.message("no.alias.type")))

  override def lowerBound: TypeResult = aliasedType

  override def upperBound: TypeResult = aliasedType

  def isExactAliasFor(cls: PsiClass): Boolean = {
    val isDefinedInObject = containingClass match {
      case obj: ScObject if obj.isStatic => true
      case _ => false
    }
    isDefinedInObject && isAliasFor(cls)
  }

  def isAliasFor(cls: PsiClass): Boolean = {
    if (cls.getTypeParameters.length != typeParameters.length) false
    else if (cls.hasTypeParameters) {
      val typeParamsAreAppliedInOrderToCorrectClass = aliasedType.getOrAny match {
        case pte: ScParameterizedType =>
          val refersToClass = pte.designator.equiv(ScalaType.designator(cls))
          val typeParamsAppliedInOrder = (pte.typeArguments corresponds typeParameters) {
            case (tpt: TypeParameterType, tp) if tpt.psiTypeParameter == tp => true
            case _ => false
          }
          refersToClass && typeParamsAppliedInOrder
        case _ => false
      }
      val varianceAndBoundsMatch = cls match {
        case sc0@(_: ScClass | _: ScTrait) =>
          val sc = sc0.asInstanceOf[ScTypeParametersOwner]
          (typeParameters corresponds sc.typeParameters) {
            case (tp1, tp2) => tp1.variance == tp2.variance && tp1.upperBound == tp2.upperBound && tp1.lowerBound == tp2.lowerBound &&
                    tp1.contextBound.isEmpty && tp2.contextBound.isEmpty && tp1.viewBound.isEmpty && tp2.viewBound.isEmpty
          }
        case _ => // Java class
          (typeParameters corresponds cls.getTypeParameters) {
            case (tp1, tp2) => tp1.variance == Invariant && tp1.upperTypeElement.isEmpty && tp2.getExtendsListTypes.isEmpty &&
                    tp1.lowerTypeElement.isEmpty && tp1.contextBound.isEmpty && tp1.viewBound.isEmpty
          }
      }
      typeParamsAreAppliedInOrderToCorrectClass && varianceAndBoundsMatch
    }
    else {
      val clsType = ScalaType.designator(cls)
      typeParameters.isEmpty && aliasedType.getOrElse(return false).equiv(clsType)
    }
  }
}