package org.jetbrains.plugins.scala
package lang
package psi
package types

import _root_.scala.collection.immutable.HashSet
import api.toplevel.ScTypeParametersOwner

/** 
* @author ilyas
*/

object ScExistentialTypeReducer {
  def reduce(quantified : ScType, wildcards: List[ScExistentialArgument]) : ScType = {
    val q = noVariantWildcards(quantified, wildcards)
    val used = collectNames(q)
    wildcards.filter (w => used.contains(w.name)) match {
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
      case ScTypeAliasType(alias, _, _, _) => HashSet.empty + alias.name
      case ScDesignatorType(elem) => HashSet.empty + elem.getName
      case ScParameterizedType (des, typeArgs) =>
        typeArgs.foldLeft(Set.empty[String]) {(curr, p) => curr ++ collectNames(p)}
      case ScExistentialArgument(_, _, lower, upper) => collectNames(lower) ++ collectNames(upper)
      case ex@ScExistentialType(q, wildcards) => {
        (wildcards.foldLeft(collectNames(q)) {(curr, w) => curr ++ collectNames(w)}) -- ex.boundNames
      }
      case _ => Set.empty 
    }
  }

  private def noVariantWildcards(t : ScType, wilds : List[ScExistentialArgument]) : ScType = t match {
    case fun@ScFunctionType(ret, params) =>
      new ScFunctionType(noVariantWildcards(ret, wilds), params.map {noVariantWildcards(_, wilds)}, fun.getProject)
    case t1@ScTupleType(comps) => new ScTupleType(comps.map {noVariantWildcards(_, wilds)}, t1.getProject)
    case ScParameterizedType (des, typeArgs) => des match {
      case ScDesignatorType(owner : ScTypeParametersOwner) => {
        val newArgs = (owner.typeParameters.toArray zip typeArgs).map ({case (tp, ta) => ta match {
          case tat : ScTypeAliasType => wilds.find{_.name == tat.name} match {
            case Some(wild) => {
              if (tp.isCovariant) wild.upperBound
              else if (tp.isContravariant) wild.lowerBound
              else tat
            }
            case None => tat
          }
          case targ => targ
          }
        })
        new ScParameterizedType(des, collection.immutable.Seq(newArgs.toSeq: _*))
      }
      case _ => t
    }
    case ScExistentialArgument(name, args, lower, upper) =>
      new ScExistentialArgument(name, args, noVariantWildcards(lower, wilds), noVariantWildcards(upper, wilds))
    case ScExistentialType(q, w1) => new ScExistentialType(noVariantWildcards(q, wilds), w1)
    case _ => t
  }
}

case class ScExistentialType(val quantified : ScType,
                             val wildcards : List[ScExistentialArgument]) extends ValueType {
  lazy val boundNames = wildcards.map {_.name}

  lazy val substitutor = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p.name, p)}

  lazy val skolem = {
    val skolemSubst = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p.name, p.unpack)}
    skolemSubst.subst(quantified)
  }
  
  override def equiv(t : ScType) = t match {
    case ex : ScExistentialType => {
        val unify = (ex.boundNames zip wildcards).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._1, p._2)}
        wildcards.zip(ex.wildcards) forall {case (w1, w2) => w1.equiv(unify.subst(w2))}
      } && (substitutor.subst(quantified) equiv ex.substitutor.subst(ex.quantified))
    case _ => false
  }
}

case class ScExistentialArgument(val name : String, val args : List[ScTypeParameterType],
                                 val lowerBound : ScType, val upperBound : ScType) extends ValueType {
  def unpack = new ScSkolemizedType(name, args, lowerBound, upperBound)

  override def equiv(t : ScType) = t match {
    case exist : ScExistentialArgument => {
      val s = (exist.args zip args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._1.name, p._2)}
      lowerBound.equiv(s.subst(exist.lowerBound)) && upperBound.equiv(s.subst(exist.upperBound))
    }
    case _ => false
  }
}

case class ScSkolemizedType(name : String, args : List[ScTypeParameterType], lower : ScType, upper : ScType)
extends ValueType {
  override def equiv(t: ScType) = this eq t
}
