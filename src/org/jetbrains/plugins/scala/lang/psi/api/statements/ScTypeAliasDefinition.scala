package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeParameterType, Equivalence, ScParameterizedType, ScType}
import base.types.ScTypeElement
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker
import stubs.ScTypeAliasStub
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import types.result.{TypeResult, Failure, TypingContext}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  def aliasedTypeElement: ScTypeElement = {
    val stub = this.asInstanceOf[ScalaStubBasedElementImpl[_ <: PsiElement]].getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeAliasStub].getTypeElement
    }

    findChildByClassScala(classOf[ScTypeElement])
  }

  def aliasedType(ctx: TypingContext): TypeResult[ScType] = {
    if (ctx.visited.contains(this)) {
      new Failure(ScalaBundle.message("circular.dependency.detected", name), Some(this)) {override def isCyclic = true}
    } else {
      aliasedTypeElement.getType(ctx(this))
    }
  }

  def aliasedType: TypeResult[ScType] = CachesUtil.get(
      this, CachesUtil.ALIASED_KEY,
      new CachesUtil.MyProvider(this, {ta: ScTypeAliasDefinition => ta.aliasedType(TypingContext.empty)})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )

  def lowerBound: TypeResult[ScType] = aliasedType(TypingContext.empty)
  def upperBound: TypeResult[ScType] = aliasedType(TypingContext.empty)

  def isExactAliasFor(cls: PsiClass): Boolean = {
    val isDefinedInObject = containingClass match {
      case obj: ScObject if obj.isStatic => true
      case _ => false
    }
    if (isDefinedInObject) {
      if (cls.getTypeParameters.length != typeParameters.length) {
        return false
      } else if (cls.hasTypeParameters) {
        val typeParamsAreAppliedInOrderToCorrectClass = aliasedType.getOrAny match {
          case pte: ScParameterizedType =>
            val refersToClass = Equivalence.equiv(pte.designator, ScType.designator(cls))
            val typeParamsAppliedInOrder = (pte.typeArgs corresponds typeParameters) {
              case (tpt: ScTypeParameterType, tp) if tpt.param == tp => true
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
        return typeParamsAreAppliedInOrderToCorrectClass && varianceAndBoundsMatch
      } else {
        val clsType = ScType.designator(cls)
        return typeParameters.isEmpty && Equivalence.equiv(aliasedType.getOrElse(return false), clsType)
      }
    }

    false
  }
}