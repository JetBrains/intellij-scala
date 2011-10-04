package org.jetbrains.plugins.scala
package lang
package psi
package types

import _root_.scala.collection.immutable.HashSet
import api.toplevel.ScTypeParametersOwner
import collection.mutable.ArrayBuffer
import api.statements.ScTypeAlias
import api.base.types.ScExistentialClause
import nonvalue._
import api.toplevel.typedef.ScTypeDefinition
import api.statements.params.ScTypeParam

/**
* @author ilyas
*/
case class ScExistentialType(quantified : ScType,
                             wildcards : List[ScExistentialArgument]) extends ValueType {

  @volatile
  private var _boundNames: List[String] = null
  def boundNames: List[String] = {
    var res = _boundNames
    if (res != null) return res
    res = boundNamesInner
    _boundNames = res
    res
  }
  private def boundNamesInner: List[String] = wildcards.map {_.name}

  @volatile
  private var _substitutor: ScSubstitutor = null

  def substitutor: ScSubstitutor = {
    var res = _substitutor
    if (res != null) return res
    res = substitutorInner
    _substitutor = res
    res
  }
  def substitutorInner: ScSubstitutor = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p.name, ""), p)}

  @volatile
  private var _skolem: ScType = null

  def skolem: ScType = {
    var res = _skolem
    if (res != null) return res
    res = skolemInner
    _skolem = res
    res
  }

  private def skolemInner: ScType = {
    val skolemSubst = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p.name, ""), p.unpack)}
    skolemSubst.subst(quantified)
  }

  override def removeAbstracts = ScExistentialType(quantified.removeAbstracts, 
    wildcards.map(_.removeAbstracts.asInstanceOf[ScExistentialArgument]))

  override def recursiveUpdate(update: ScType => (Boolean, ScType)): ScType = {
    update(this) match {
      case (true, res) => res
      case _ =>
        try {
          ScExistentialType(quantified.recursiveUpdate(update),
            wildcards.map(_.recursiveUpdate(update).asInstanceOf[ScExistentialArgument]))
        }
        catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        try {
          ScExistentialType(quantified.recursiveVarianceUpdate(update, variance),
            wildcards.map(_.recursiveVarianceUpdate(update, variance).asInstanceOf[ScExistentialArgument]))
        }
        catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    val simplified = simplify()
    if (this != simplified) return Equivalence.equivInner(simplified, r, undefinedSubst, falseUndef)
    r match {
      case ex: ScExistentialType => {
        val simplified = ex.simplify()
        if (ex != simplified) return Equivalence.equivInner(this, simplified, undefinedSubst, falseUndef)
        val unify = (ex.boundNames zip wildcards).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._1, ""), p._2)}
        val list = wildcards.zip(ex.wildcards)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next()
          val t = Equivalence.equivInner(w1, unify.subst(w2), undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        Equivalence.equivInner(substitutor.subst(quantified), ex.substitutor.subst(ex.quantified), undefinedSubst, falseUndef)
      }
      case _ => (false, undefinedSubst)
    }
  }

  /** Specification 3.2.10:
    * 1. Multiple for-clauses in an existential type can be merged. E.g.,
    * T forSome {Q} forSome {H} is equivalent to T forSome {Q;H}.
    * 2. Unused quantifications can be dropped. E.g., T forSome {Q;H} where
    * none of the types defined in H are referred to by T or Q, is equivalent to
    * T forSome {Q}.
    * 3. An empty quantification can be dropped. E.g., T forSome { } is equivalent
    * to T.
    * 4. An existential type T forSome {Q} where Q contains a clause
    * type t[tps] >: L <: U is equivalent to the type T' forSome {Q} where
    * T' results from T by replacing every covariant occurrence (4.5) of t in T by
    * U and by replacing every contravariant occurrence of t in T by L.
    */
  def simplify(): ScType = {
    //second rule
    val usedWildcards: ArrayBuffer[ScExistentialArgument] = new ArrayBuffer[ScExistentialArgument]
    def checkRecursive(tp: ScType, rejected: HashSet[String]) {
      tp match {
        case JavaArrayType(arg) => checkRecursive(arg, rejected)
        case ScAbstractType(tpt, lower, upper) =>
          checkRecursive(tpt, rejected)
          checkRecursive(lower, rejected)
          checkRecursive(upper, rejected)
        case c@ScCompoundType(comps, decls, typeDecls, _) =>
          val newSet = rejected ++ typeDecls.map(_.getName)
          comps.foreach(checkRecursive(_, newSet))
          c.signatureMap.foreach(tuple => checkRecursive(tuple._2, newSet))
          c.types.foreach(tuple => {
            checkRecursive(tuple._2._1, newSet)
            checkRecursive(tuple._2._1, newSet)
          })
        case ScDesignatorType(elem) =>
          elem match {
            case ta: ScTypeAlias if ta.getContext.isInstanceOf[ScExistentialClause] =>
              wildcards.foreach(arg => if (arg.name == ta.name && !rejected.contains(arg.name)) {
                usedWildcards += arg
              })
            case _ =>
          }
        case ScTypeVariable(name) =>
          wildcards.foreach(arg => if (arg.name == name && !rejected.contains(arg.name)) {
            usedWildcards += arg
          })
        case ex: ScExistentialArgument => if (!rejected.contains(ex.name)) usedWildcards += ex //is it important?
        case ex: ScExistentialType =>
          val newSet = if (ex ne this) rejected ++ ex.wildcards.map(_.name) else rejected
          checkRecursive(ex.quantified, newSet)
          ex.wildcards.foreach(ex => {
            checkRecursive(ex.lowerBound, newSet)
            checkRecursive(ex.upperBound, newSet)
          })
        case ScFunctionType(returnType, params) =>
          checkRecursive(returnType, rejected)
          params.foreach(checkRecursive(_, rejected))
        case ScTupleType(components) =>
          components.foreach(checkRecursive(_, rejected))
        case ScProjectionType(projected, element, subst) =>
          checkRecursive(projected, rejected)
        case ScParameterizedType(designator, typeArgs) =>
          checkRecursive(designator, rejected)
          typeArgs.foreach(checkRecursive(_, rejected))
        case ScTypeParameterType(name, args, lower, upper, param) =>
//          checkRecursive(lower.v, rejected)
//          checkRecursive(upper.v, rejected)
//          args.foreach(checkRecursive(_, rejected))
        case ScSkolemizedType(name, args, lower, upper) =>
          checkRecursive(lower, rejected)
          checkRecursive(upper, rejected)
          args.foreach(checkRecursive(_, rejected))
        case ScUndefinedType(tpt) => checkRecursive(tpt, rejected)
        case ScMethodType(returnType, params, isImplicit) =>
          checkRecursive(returnType, rejected)
          params.foreach(p => checkRecursive(p.paramType, rejected))
        case ScTypePolymorphicType(internalType, typeParameters) =>
          checkRecursive(internalType, rejected)
          typeParameters.foreach(tp => {
            checkRecursive(tp.lowerType, rejected)
            checkRecursive(tp.upperType, rejected)
          })
        case _ =>
      }
    }

    checkRecursive(this, HashSet.empty)
    val used = wildcards.filter(arg => usedWildcards.contains(arg))
    if (used.length == 0) return quantified
    if (used.length != wildcards.length) return ScExistentialType(quantified, used).simplify()

    //first rule
    quantified match {
      case ScExistentialType(quantified, wildcards) =>
        return ScExistentialType(quantified, wildcards ++ this.wildcards).simplify()
      case _ =>
    }

    //third rule
    if (wildcards.length == 0) return quantified

    var updated = false
    //fourth rule
    def updateRecursive(tp: ScType, rejected: HashSet[String], variance: Int): ScType = {
      if (variance == 0) return tp //optimization
      tp match {
        case _: StdType => tp
        case f@ScFunctionType(returnType, params) =>
          ScFunctionType(updateRecursive(returnType, rejected, variance),
            params.map(updateRecursive(_, rejected, -variance)))(f.getProject, f.getScope)
        case t@ScTupleType(components) =>
          ScTupleType(components.map(updateRecursive(_, rejected, variance)))(t.getProject, t.getScope)
        case c@ScCompoundType(components, decls, typeDecls, subst) =>
          val newSet = rejected ++ typeDecls.map(_.getName)
          new ScCompoundType(components, decls, typeDecls, subst, c.signatureMap.map {
            case (sign, tp) => (sign, updateRecursive(tp, newSet, variance))
          }, c.types.map {
            case (s, (tp1, tp2)) => (s, (updateRecursive(tp1, newSet, variance), updateRecursive(tp2, newSet, -variance)))
          }, c.problems.toList)
        case ScProjectionType(_, _, _) => tp
        case JavaArrayType(_) => tp
        case ScParameterizedType(designator, typeArgs) =>
          val parameteresIterator = designator match {
            case tpt: ScTypeParameterType =>
              tpt.args.map(_.param).iterator
            case undef: ScUndefinedType =>
              undef.tpt.args.map(_.param).iterator
            case tp: ScType =>
              ScType.extractClass(tp) match {
                case Some(owner) => {
                  owner match {
                    case td: ScTypeDefinition => td.typeParameters.iterator
                    case _ => owner.getTypeParameters.iterator
                  }
                }
                case _ => return tp
              }
          }
          val typeArgsIterator = typeArgs.iterator
          val newTypeArgs = new ArrayBuffer[ScType]()
          while (parameteresIterator.hasNext && typeArgsIterator.hasNext) {
            val param = parameteresIterator.next()
            val arg = typeArgsIterator.next()
            param match {
              case tp: ScTypeParam if tp.isCovariant =>
                newTypeArgs += updateRecursive (arg, rejected, variance)
              case tp: ScTypeParam if tp.isContravariant =>
                newTypeArgs += updateRecursive (arg, rejected, -variance)
              case _ =>
                newTypeArgs += arg
            }
          }
          ScParameterizedType(designator, newTypeArgs)
        case ex@ScExistentialType(quantified, wildcards) =>
          val newSet = if (ex ne this) rejected ++ ex.wildcards.map(_.name) else rejected
          ScExistentialType(updateRecursive(quantified, newSet, variance),
            wildcards.map(arg => ScExistentialArgument(arg.name, arg.args.map(arg =>
              updateRecursive(arg, newSet, -variance).asInstanceOf[ScTypeParameterType]),
              updateRecursive(arg.lowerBound, newSet, -variance), updateRecursive(arg.upperBound, newSet, variance))))
        case ScThisType(clazz) => tp
        case ScDesignatorType(element) => element match {
          case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause] =>
            if (!rejected.contains(a.getName)) {
              wildcards.find(_.name == a.getName) match {
                case Some(arg) => variance match {
                  case 1 =>
                    updated = true
                    arg.upperBound
                  case -1 =>
                    updated = true
                    arg.lowerBound
                  case 0 => tp
                }
                case _ => tp
              }
            } else tp
          case _ => tp
        }
        case ScTypeVariable(name) =>
          if (!rejected.contains(name)) {
            wildcards.find(_.name == name) match {
              case Some(arg) => variance match {
                case 1 =>
                  updated = true
                  arg.upperBound
                case -1 =>
                  updated = true
                  arg.lowerBound
                case 0 => tp
              }
              case _ => tp
            }
          } else tp
        case ScTypeParameterType(name, args, lower, upper, param) =>
          //should return TypeParameterType (for undefined type)
          tp
          /*ScTypeParameterType(name, args.map(arg =>
            updateRecursive(arg, rejected, -variance).asInstanceOf[ScTypeParameterType]),
            new Suspension[ScType](updateRecursive(lower.v, rejected, -variance)),
            new Suspension[ScType](updateRecursive(upper.v, rejected, variance)), param)*/
        case ScExistentialArgument(name, args, lowerBound, upperBound) =>
          if (!rejected.contains(name)) {
            variance match {
              case 1 =>
                updated = true
                upperBound
              case -1 =>
                updated = true
                lowerBound
              case 0 => tp
            }
          } else tp
        case ScSkolemizedType(name, args, lower, upper) =>
          ScSkolemizedType(name, args.map(arg =>
            updateRecursive(arg, rejected, -variance).asInstanceOf[ScTypeParameterType]),
            updateRecursive(lower, rejected, -variance),
            updateRecursive(upper, rejected, variance))
        case ScUndefinedType(tpt) => ScUndefinedType(
          updateRecursive(tpt, rejected, variance).asInstanceOf[ScTypeParameterType]
        )
        case m@ScMethodType(returnType, params, isImplicit) =>
          ScMethodType(updateRecursive(returnType, rejected, variance),
            params.map(param => param.copy(paramType = updateRecursive(param.paramType, rejected, -variance))),
            isImplicit)(m.project, m.scope)
        case ScAbstractType(tpt, lower, upper) =>
          ScAbstractType(updateRecursive(tpt, rejected, variance).asInstanceOf[ScTypeParameterType],
            updateRecursive(lower, rejected, -variance),
            updateRecursive(upper, rejected, variance))
        case ScTypePolymorphicType(internalType, typeParameters) =>
          ScTypePolymorphicType(
            updateRecursive(internalType, rejected, variance),
            typeParameters.map(tp => TypeParameter(tp.name,
              updateRecursive(tp.lowerType, rejected, variance),
              updateRecursive(tp.upperType, rejected, variance),
              tp.ptp
            ))
          )
        case _ => tp
      }
    }

    val res = updateRecursive(this, HashSet.empty, 1)
    if (updated) {
      res match {
        case ex: ScExistentialType if ex != this => ex.simplify()
        case _ => res
      }
    } else this
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitExistentialType(this)
  }
}

case class ScExistentialArgument(name : String, args : List[ScTypeParameterType],
                                 lowerBound : ScType, upperBound : ScType) extends ValueType {
  def unpack = new ScSkolemizedType(name, args, lowerBound, upperBound)

  override def removeAbstracts = ScExistentialArgument(name, args, lowerBound.removeAbstracts, upperBound.removeAbstracts)

  override def recursiveUpdate(update: ScType => (Boolean, ScType)): ScType = {
    update(this) match {
      case (true, res) => res
      case _ =>
        ScExistentialArgument(name, args, lowerBound.recursiveUpdate(update), upperBound.recursiveUpdate(update))
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        ScExistentialArgument(name, args, lowerBound.recursiveVarianceUpdate(update, -variance),
          upperBound.recursiveVarianceUpdate(update, variance))
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case exist: ScExistentialArgument => {
        val s = (exist.args zip args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._1.name, ""), p._2)}
        val t = Equivalence.equivInner(lowerBound, s.subst(exist.lowerBound), undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        Equivalence.equivInner(upperBound, s.subst(exist.upperBound), undefinedSubst, falseUndef)
      }
      case _ => (false, undefinedSubst)
    }
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitExistentialArgument(this)
  }
}

case class ScSkolemizedType(name : String, args : List[ScTypeParameterType], lower : ScType, upper : ScType)
  extends ValueType {
  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitSkolemizedType(this)
  }

  override def removeAbstracts = ScSkolemizedType(name, args, lower.removeAbstracts, upper.removeAbstracts)

  override def recursiveUpdate(update: ScType => (Boolean, ScType)): ScType = {
    update(this) match {
      case (true, res) => res
      case _ =>
        ScSkolemizedType(name, args, lower.recursiveUpdate(update), upper.recursiveUpdate(update))
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        ScSkolemizedType(name, args, lower.recursiveVarianceUpdate(update, -variance),
          upper.recursiveVarianceUpdate(update, variance))
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var u = uSubst
    r match {
      case ScSkolemizedType(rname, rargs, rlower, rupper) =>
        if (name != rname) return (false, uSubst)
        if (args.length != rargs.length) return (false, uSubst)
        args.zip(rargs) foreach {
          case (tpt1, tpt2) =>
            val t = Equivalence.equivInner(tpt1, tpt2, u, falseUndef)
            if (!t._1) return (false, u)
            u = t._2
        }
        var t = Equivalence.equivInner(lower, rlower, u, falseUndef)
        if (!t._1) return (false, u)
        u = t._2
        t = Equivalence.equivInner(upper, rupper, u, falseUndef)
        if (!t._1) return (false, u)
        u = t._2
        (true, u)
      case _ => (false, uSubst)
    }
  }
}
