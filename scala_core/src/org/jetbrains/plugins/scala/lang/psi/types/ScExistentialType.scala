package org.jetbrains.plugins.scala.lang.psi.types

import _root_.scala.collection.immutable.HashSet
import api.toplevel.ScTypeParametersOwner

/** 
* @author ilyas
*/

object ScExistentialTypeReducer {
  def reduce(quantified : ScType, wildcards: List[Pair[String, ScWildcardType]]) : ScType = {
    val q = noVariantWildcards(quantified, wildcards)
    val used = collectNames(q)
    wildcards.filter (p => used.contains(p._1)) match {
      case Nil => q
      case usedWildcards => q match {
        case ScExistentialType(q1, w1) => new ScExistentialType(q1, w1 ::: usedWildcards)
        case _ => new ScExistentialType(q, usedWildcards)
      }
    }
  }

  private def collectNames (t : ScType) : Set[String] = {
    t match {
      case ScFunctionType(ret, params) => params.foldLeft(collectNames(ret)) {(curr, p) => curr ++ collectNames(p)}
      case ScTupleType(comps) => comps.foldLeft(Set.empty[String]) {(curr, p) => curr ++ collectNames(p)}
      case ScTypeAliasDesignatorType(a, _) => HashSet.empty + a.name
      case ScParameterizedType (des, typeArgs) =>
        typeArgs.foldLeft(Set.empty[String]) {(curr, p) => curr ++ collectNames(p)}
      case ScWildcardType(lower, upper) => collectNames(lower) ++ collectNames(upper)
      case ex@ScExistentialType(q, wildcards) => {
        (wildcards.foldLeft(collectNames(q)) {(curr, p) => curr ++ collectNames(p._2)}) -- ex.boundNames
      }
      case _ => Set.empty 
    }
  }

  private def noVariantWildcards(t : ScType, wilds : List[Pair[String, ScWildcardType]]) : ScType = t match {
    case ScFunctionType(ret, params) =>
      new ScFunctionType(noVariantWildcards(ret, wilds), params.map {noVariantWildcards(_, wilds)})
    case ScTupleType(comps) => new ScTupleType(comps.map {noVariantWildcards(_, wilds)})
    case ScParameterizedType (des, typeArgs) => des match {
      case ScDesignatorType(owner : ScTypeParametersOwner) => {
        val newArgs = (owner.typeParameters.toArray zip typeArgs).map ({p => p._2 match {
          case tadt@ScTypeAliasDesignatorType(a, s) => wilds.find{_._1 == a.name} match {
            case Some(wild) => {
              val tp = p._1
              if (tp.isCovariant) s.subst(wild._2.upperBound)
              else if (tp.isContravariant) s.subst(wild._2.lowerBound)
              else tadt
            }
            case None => tadt
          }
          case targ => targ
          }
        })
        new ScParameterizedType(des, newArgs)
      }
      case _ => t
    }
    case ScWildcardType(lower, upper) =>
      new ScWildcardType(noVariantWildcards(lower, wilds), noVariantWildcards(upper, wilds))
    case ScExistentialType(q, w1) => new ScExistentialType(noVariantWildcards(q, wilds), w1)
    case _ => t
  }
}

case class ScExistentialType(val quantified : ScType,
                             val wildcards : List[Pair[String, ScWildcardType]]) extends ScType {
  val boundNames = wildcards.map {_._1}
  val boundTypes = wildcards.map {_._2}

  val substitutor = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s + (p._1, p._2)}
  
  override def equiv(t : ScType) = t match {
    case ex : ScExistentialType => {
        val unify = (ex.boundNames zip boundTypes).foldLeft(ScSubstitutor.empty) {(s, p) => s + (p._1, p._2)}
        wildcards.equalsWith(ex.wildcards) {(w1, w2) => w1._2.equiv(unify.subst(w2._2))}
      } && (substitutor.subst(quantified) equiv ex.substitutor.subst(ex.quantified))
    case _ => false
  }
}

case class ScWildcardType(val lowerBound : ScType, val upperBound : ScType) extends ScType {
  override def equiv(t : ScType) = t match {
    case wild : ScWildcardType => lowerBound.equiv(wild.lowerBound) && upperBound.equiv(wild.upperBound)
  }
}