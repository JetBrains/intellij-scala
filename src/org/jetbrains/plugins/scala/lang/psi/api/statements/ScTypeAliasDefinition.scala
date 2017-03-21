package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  override def isDefinition: Boolean = true

  def aliasedTypeElement: Option[ScTypeElement]

  def aliasedType(ctx: TypingContext = TypingContext.empty): TypeResult[ScType] = {
    if (ctx.visited.contains(this)) {
      new Failure(ScalaBundle.message("circular.dependency.detected", name), Some(this)) {
        override def isCyclic = true
      }
    } else {
      aliasedTypeElement.map {
        _.getType(ctx(this))
      }.getOrElse(Failure("No alias type", Some(this)))
    }
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def aliasedType: TypeResult[ScType] = aliasedType()

  def lowerBound: TypeResult[ScType] = aliasedType()

  def upperBound: TypeResult[ScType] = aliasedType()

  def isExactAliasFor(cls: PsiClass)(implicit typeSystem: TypeSystem): Boolean = {
    val isDefinedInObject = containingClass match {
      case obj: ScObject if obj.isStatic => true
      case _ => false
    }
    isDefinedInObject && isAliasFor(cls)
  }

  def isAliasFor(cls: PsiClass)(implicit typeSystem: TypeSystem): Boolean = {
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
            case (tp1, tp2) => tp1.variance == ScTypeParam.Invariant && tp1.upperTypeElement.isEmpty && tp2.getExtendsListTypes.isEmpty &&
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