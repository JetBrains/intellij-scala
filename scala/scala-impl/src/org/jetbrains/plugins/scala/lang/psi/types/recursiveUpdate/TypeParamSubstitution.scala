package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.api.statements.params
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{TypeParamId, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScAbstractType, ScLiteralType, ScType}

import scala.collection.immutable.LongMap

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private case class TypeParamSubstitution(tvMap: LongMap[ScType]) extends LeafSubstitution {

  override def toString: String = tvMap.map {
    case (id, tp) => params.typeParamName(id) + " -> " + tp.toString
  }.mkString("Map(", ", ", ")")

  override protected val subst: PartialFunction[LeafType, ScType] = {
    case a: ScAbstractType     => updatedAbstract(a)
    case u: UndefinedType      => updatedUndefined(u)
    case t: TypeParameterType  => updatedTypeParameter(t)
  }

  private def updatedAbstract(a: ScAbstractType): ScType = {
    val typeParameter = a.typeParameter
    tvMap.getOrElse(typeParameter.typeParamId, null) match {
      case null => a
      case v => v match {
        case tpt: TypeParameterType if tpt.psiTypeParameter == typeParameter.psiTypeParameter => a
        case _ => extractDesignator(typeParameter, v)
      }
    }
  }

  private def updatedUndefined(u: UndefinedType): ScType = {
    val typeParameter = u.typeParameter
    tvMap.getOrElse(typeParameter.typeParamId, null) match {
      case null => u
      case v => v match {
        case tpt: TypeParameterType if tpt.psiTypeParameter == typeParameter.psiTypeParameter => u
        case _ => extractDesignator(typeParameter, v)
      }
    }
  }

  private def updatedTypeParameter(tpt: TypeParameterType): ScType = {
    tvMap.getOrElse(tpt.typeParamId, null) match {
      case null => tpt
      case v: ScLiteralType => v.blockWiden
      case v => extractDesignator(tpt.typeParameter, v)
    }
  }

  private def extractDesignator(tpt: TypeParameter, t: ScType): ScType = {
    if (tpt.typeParameters.isEmpty) t
    else t match {
      case ParameterizedType(designator, _) => designator
      case _ => t
    }
  }
}

private object TypeParamSubstitution {
  def buildMap[T, S](typeParamsLike: Iterable[T],
                     types: Iterable[S],
                     initial: LongMap[ScType] = LongMap.empty)
                    (toScType: S => ScType)
                    (implicit ev: TypeParamId[T]): LongMap[ScType] = {
    val iterator1 = typeParamsLike.iterator
    val iterator2 = types.iterator
    var result = initial
    while (iterator1.hasNext && iterator2.hasNext) {
      result = result.updated(ev.typeParamId(iterator1.next()), toScType(iterator2.next()))
    }
    result
  }
}

