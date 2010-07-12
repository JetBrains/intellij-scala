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
      case JavaArrayType(arg) => collectNames(arg)
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
      new ScFunctionType(noVariantWildcards(ret, wilds), params.map {noVariantWildcards(_, wilds)},
        fun.getProject, fun.getScope)
    case t1@ScTupleType(comps) => new ScTupleType(comps.map {noVariantWildcards(_, wilds)}, t1.getProject)
    //todo: case: JavaArrayType?
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

  lazy val substitutor = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p.name, ""), p)}

  lazy val skolem = {
    val skolemSubst = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p.name, ""), p.unpack)}
    skolemSubst.subst(quantified)
  }

  override def removeAbstracts = ScExistentialType(quantified.removeAbstracts, 
    wildcards.map(_.removeAbstracts.asInstanceOf[ScExistentialArgument]))
}

case class ScExistentialArgument(val name : String, val args : List[ScTypeParameterType],
                                 val lowerBound : ScType, val upperBound : ScType) extends ValueType {
  def unpack = new ScSkolemizedType(name, args, lowerBound, upperBound)

  override def removeAbstracts = ScExistentialArgument(name, args, lowerBound.removeAbstracts, upperBound.removeAbstracts)
}

case class ScSkolemizedType(name : String, args : List[ScTypeParameterType], lower : ScType, upper : ScType)
extends ValueType {
  override def removeAbstracts = ScSkolemizedType(name, args, lower.removeAbstracts, upper.removeAbstracts)
}
