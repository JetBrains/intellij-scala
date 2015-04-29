package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.lang.ref.WeakReference

import com.intellij.util.containers.{WeakHashMap, ConcurrentWeakHashMap}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.Interner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScExistentialClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._

import scala.collection.immutable.{HashSet, Set}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
* @author ilyas
*/
class ScExistentialType private (val quantified : ScType,
                                 val wildcards : List[ScExistentialArgument]) extends ValueType {

  override def equals(other: scala.Any): Boolean = other match {
    case e: ScExistentialType =>
      e.quantified == quantified &&
        e.wildcards == wildcards
    case _ => false
  }

  override val hashCode: Int = 11 + quantified.hashCode() + 31 * wildcards.hashCode()

  override def toString: String = s"ScExistentialType($quantified, $wildcards)"

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
  private var _skolem: ScType = null

  def skolem: ScType = {
    var res = _skolem
    if (res != null) return res
    res = skolemInner
    _skolem = res
    res
  }

  private def skolemInner: ScType = {
    def update(tp: ScType, unpacked: Map[ScExistentialArgument, ScSkolemizedType]): ScType = {
      tp.recursiveVarianceUpdateModifiable(new HashSet[String], (tp: ScType, _: Int, rejected: HashSet[String]) => {
        tp match {
          case ScDesignatorType(element) => element match {
            case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause] =>
              if (!rejected.contains(a.name)) {
                wildcards.find(_.name == a.name) match {
                  case Some(arg) => (true, unpacked.getOrElse(arg, tp), rejected)
                  case _ => (true, tp, rejected)
                }
              } else (true, tp, rejected)
            case _ => (true, tp, rejected)
          }
          case ScTypeVariable(name) =>
            if (!rejected.contains(name)) {
              wildcards.find(_.name == name) match {
                case Some(arg) => (true, unpacked.getOrElse(arg, tp), rejected)
                case _ => (true, tp, rejected)
              }
            } else (true, tp, rejected)
          case c@ScCompoundType(components, _, typeMap) =>
            val newSet = rejected ++ typeMap.keys
            (false, c, newSet)
          case ex@ScExistentialType(_quantified, _wildcards) =>
            val newSet = if (ex ne this) rejected ++ ex.wildcards.map(_.name) else rejected //todo: for wildcards add ex.wildcards
            (false, ex, newSet)
          case _ => (false, tp, rejected)
        }
      })
    }

    def unpack(ex: ScExistentialArgument, deep: Int = 2): ScSkolemizedType = {
      if (deep == 0) {
        ScSkolemizedType(ex.name, ex.args, types.Nothing, types.Any)
      } else {
        val unpacked: Map[ScExistentialArgument, ScSkolemizedType] = wildcards.map(w => (w, unpack(w, deep - 1))).toMap
        ScSkolemizedType(ex.name, ex.args, update(ex.lowerBound, unpacked), update(ex.upperBound, unpacked))
      }
    }

    val unpacked = wildcards.map(w => (w, unpack(w))).toMap
    update(quantified, unpacked)
  }

  override def removeAbstracts = ScExistentialType(quantified.removeAbstracts, 
    wildcards.map(_.withoutAbstracts))

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        try {
          ScExistentialType(quantified.recursiveUpdate(update, newVisited),
            wildcards.map(_.recursiveUpdate(update, newVisited)))
        } catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        try {
          ScExistentialType(quantified.recursiveVarianceUpdateModifiable(newData, update, variance),
            wildcards.map(_.recursiveVarianceUpdateModifiable(newData, update, variance)))
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
    quantified match {
      case ScParameterizedType(a: ScAbstractType, args) if !falseUndef =>
        val subst = new ScSubstitutor(Map(a.tpt.args.zip(args).map {
          case (tpt: ScTypeParameterType, tp: ScType) =>
            ((tpt.param.name, ScalaPsiUtil.getPsiElementId(tpt.param)), tp)
        }: _*), Map.empty, None)
        val upper: ScType =
          subst.subst(a.upper) match {
            case ScParameterizedType(u, _) => ScExistentialType(ScParameterizedType(u, args), wildcards)
            case u => ScExistentialType(ScParameterizedType(u, args), wildcards)
          }
        val t = Conformance.conformsInner(upper, r, Set.empty, undefinedSubst)
        if (!t._1) return t

        val lower: ScType =
          subst.subst(a.lower) match {
            case ScParameterizedType(l, _) => ScExistentialType(ScParameterizedType(l, args), wildcards)
            case l => ScExistentialType(ScParameterizedType(l, args), wildcards)
          }
        return Conformance.conformsInner(r, lower, Set.empty, t._2)
      case ScParameterizedType(a: ScUndefinedType, args) if !falseUndef =>
        r match {
          case ScParameterizedType(des, _) =>
            val tpt = a.tpt
            undefinedSubst = undefinedSubst.addLower((tpt.name, tpt.getId), des)
            undefinedSubst = undefinedSubst.addUpper((tpt.name, tpt.getId), des)
            return Equivalence.equivInner(ScExistentialType(ScParameterizedType(des, args), wildcards), r, undefinedSubst, falseUndef)
          case ScExistentialType(ScParameterizedType(des, _), _) =>
            val tpt = a.tpt
            undefinedSubst = undefinedSubst.addLower((tpt.name, tpt.getId), des)
            undefinedSubst = undefinedSubst.addUpper((tpt.name, tpt.getId), des)
            return Equivalence.equivInner(ScExistentialType(ScParameterizedType(des, args), wildcards), r, undefinedSubst, falseUndef)
          case _ => return (false, undefinedSubst) //looks like something is wrong
        }
      case _ =>
    }
    r match {
      case ex: ScExistentialType =>
        val simplified = ex.simplify()
        if (ex != simplified) return Equivalence.equivInner(this, simplified, undefinedSubst, falseUndef)
        val list = wildcards.zip(ex.wildcards)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next()
          val t = w2.equivInner(w1, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        Equivalence.equivInner(skolem, ex.skolem, undefinedSubst, falseUndef) //todo: probable problems with different positions of skolemized types.
      case _ => (false, undefinedSubst)
    }
  }

  def wildcardsMap(): mutable.HashMap[ScExistentialArgument, Seq[ScType]] = {
    val res = mutable.HashMap.empty[ScExistentialArgument, Seq[ScType]]
    //todo: use recursiveVarianceUpdateModifiable?
    def checkRecursive(tp: ScType, rejected: HashSet[String]) {
      tp match {
        case JavaArrayType(arg) => checkRecursive(arg, rejected)
        case ScAbstractType(tpt, lower, upper) =>
          checkRecursive(tpt, rejected)
          checkRecursive(lower, rejected)
          checkRecursive(upper, rejected)
        case c@ScCompoundType(comps, signatureMap, typeMap) =>
          val newSet = rejected ++ typeMap.keys
          comps.foreach(checkRecursive(_, newSet))
          signatureMap.foreach {
            case (s, rt) =>
              s.substitutedTypes.foreach(_.foreach(f => checkRecursive(f(), newSet)))
              s.typeParams.foreach {
                case tParam: TypeParameter => 
                  tParam.update {
                    case tp: ScType => checkRecursive(tp, newSet); tp
                  }
              }
              checkRecursive(rt, newSet)
          }
          typeMap.foreach(_._2.updateTypes {
            case tp: ScType => checkRecursive(tp, newSet); tp
          })
        case ScDesignatorType(elem) =>
          elem match {
            case ta: ScTypeAlias if ta.getContext.isInstanceOf[ScExistentialClause] =>
              wildcards.foreach(arg => if (arg.name == ta.name && !rejected.contains(arg.name)) {
                res.update(arg, res.getOrElse(arg, Seq.empty[ScType]) ++ Seq(tp))
              })
            case _ =>
          }
        case ScTypeVariable(name) =>
          wildcards.foreach(arg => if (arg.name == name && !rejected.contains(arg.name)) {
            res.update(arg, res.getOrElse(arg, Seq.empty[ScType]) ++ Seq(tp))
          })
        case ex: ScExistentialType =>
          var newSet = if (ex ne this) rejected ++ ex.wildcards.map(_.name) else rejected
          checkRecursive(ex.quantified, newSet)
          if (ex eq this) newSet = rejected ++ ex.wildcards.map(_.name)
          ex.wildcards.foreach(ex => {
            checkRecursive(ex.lowerBound, newSet)
            checkRecursive(ex.upperBound, newSet)
          })
        case ScProjectionType(projected, element, _) =>
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
            checkRecursive(tp.lowerType(), rejected)
            checkRecursive(tp.upperType(), rejected)
          })
        case _ =>
      }
    }
    checkRecursive(this, HashSet.empty)
    wildcards.foreach {
      case ScExistentialArgument(_, args, lower, upper) =>
        checkRecursive(lower, HashSet.empty)
        checkRecursive(upper, HashSet.empty)
    }
    res
  }

  //todo: use recursiveVarianceUpdateModifiable?
  private def updateRecursive(tp: ScType, rejected: HashSet[String] = HashSet.empty, variance: Int = 1)
                             (implicit update: (Int, ScExistentialArgument, ScType) => ScType): ScType = {
    if (variance == 0) return tp //optimization
    tp match {
      case _: StdType => tp
      case c@ScCompoundType(components, signatureMap, typeMap) =>
        val newSet = rejected ++ typeMap.keys

        def updateTypeParam(tp: TypeParameter): TypeParameter = {
          new TypeParameter(tp.name, tp.typeParams.map(updateTypeParam), {
            val res = updateRecursive(tp.lowerType(), newSet, variance)
            () => res
          }, {
            val res = updateRecursive(tp.upperType(), newSet, -variance)
            () => res
          }, tp.ptp)
        }

        ScCompoundType(components, signatureMap.map {
          case (s, sctype) =>
            val pTypes: List[Seq[() => ScType]] =
              s.substitutedTypes.map(_.map(f => () => updateRecursive(f(), newSet, variance)))
            val tParams: Array[TypeParameter] = if (s.typeParams.length == 0) TypeParameter.EMPTY_ARRAY else s.typeParams.map(updateTypeParam)
            val rt: ScType = updateRecursive(sctype, newSet, -variance)
            (new Signature(s.name, pTypes, s.paramLength, tParams,
              ScSubstitutor.empty, s.namedElement match {
                case fun: ScFunction =>
                  ScFunction.getCompoundCopy(pTypes.map(_.map(_()).toList), tParams.toList, rt, fun)
                case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
                case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
                case named => named
              }, s.hasRepeatedParam), rt)
        }, typeMap.map {
          case (s, sign) => (s, sign.updateTypesWithVariance(updateRecursive(_, newSet, _), variance))
        })
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
              case Some(owner) =>
                owner match {
                  case td: ScTypeDefinition => td.typeParameters.iterator
                  case _ => owner.getTypeParameters.iterator
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
        ScParameterizedType(updateRecursive(designator, rejected, variance), newTypeArgs)
      case ex@ScExistentialType(_quantified, _wildcards) =>
        var newSet = if (ex ne this) rejected ++ ex.wildcards.map(_.name) else rejected
        val q = updateRecursive(_quantified, newSet, variance)
        if (ex eq this) newSet = rejected ++ ex.wildcards.map(_.name)
        ScExistentialType(q, _wildcards.map(arg => ScExistentialArgument(arg.name, arg.args.map(arg =>
          updateRecursive(arg, newSet, -variance).asInstanceOf[ScTypeParameterType]),
          updateRecursive(arg.lowerBound, newSet, -variance), updateRecursive(arg.upperBound, newSet, variance))))
      case ScThisType(clazz) => tp
      case ScDesignatorType(element) => element match {
        case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause] =>
          if (!rejected.contains(a.name)) {
            wildcards.find(_.name == a.name) match {
              case Some(arg) => update(variance, arg, tp)
              case _ => tp
            }
          } else tp
        case _ => tp
      }
      case ScTypeVariable(name) =>
        if (!rejected.contains(name)) {
          wildcards.find(_.name == name) match {
            case Some(arg) => update(variance, arg, tp)
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
          typeParameters.map(tp => TypeParameter(tp.name, tp.typeParams /* todo: is it important here to update? */,
            () => updateRecursive(tp.lowerType(), rejected, variance),
            () => updateRecursive(tp.upperType(), rejected, variance),
            tp.ptp
          ))
        )
      case _ => tp
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
    val usedWildcards = wildcardsMap().keySet

    val used = wildcards.filter(arg => usedWildcards.contains(arg))
    if (used.isEmpty) return quantified
    if (used.length != wildcards.length) return ScExistentialType(quantified, used).simplify()

    //first rule
    quantified match {
      case ScExistentialType(_quantified, _wildcards) =>
        return ScExistentialType(_quantified, _wildcards ++ this.wildcards).simplify()
      case _ =>
    }

    //third rule
    if (wildcards.isEmpty) return quantified

    var updated = false
    //fourth rule
    def hasWildcards(tp: ScType): Boolean = {
      var res = false
      tp.recursiveUpdate {
        case tp@ScDesignatorType(element) => element match {
          case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause]
                  && wildcards.exists(_.name == a.name) =>
            res = true
            (res, tp)
          case _ => (res,  tp)
        }
        case tp@ScTypeVariable(name) if wildcards.exists(_.name == name) =>
          res = true
          (res, tp)
        case tp: ScType => (res, tp)
      }
      res
    }
    val res = updateRecursive(this, HashSet.empty, 1) {
      case (variance, arg, tp) =>
        variance match {
          case 1 if !hasWildcards(arg.upperBound)=>
            updated = true
            arg.upperBound
          case -1 if !hasWildcards(arg.lowerBound)=>
            updated = true
            arg.lowerBound
          case _ => tp
        }
    }
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

  override def typeDepth: Int = {
    def typeParamsDepth(typeParams: List[ScTypeParameterType]): Int = {
      typeParams.map {
        case typeParam =>
          val boundsDepth = typeParam.lower.v.typeDepth.max(typeParam.upper.v.typeDepth)
          if (typeParam.args.nonEmpty) {
            (typeParamsDepth(typeParam.args) + 1).max(boundsDepth)
          } else boundsDepth
      }.max
    }

    val quantDepth = quantified.typeDepth
    if (wildcards.nonEmpty) {
      (wildcards.map {
        wildcard =>
          val boundsDepth = wildcard.lowerBound.typeDepth.max(wildcard.upperBound.typeDepth)
          if (wildcard.args.nonEmpty) {
            (typeParamsDepth(wildcard.args) + 1).max(boundsDepth)
          } else boundsDepth
      }.max + 1).max(quantDepth)
    } else quantDepth
  }
}

object ScExistentialType {
  def simpleExistential(name: String, args: List[ScTypeParameterType], lowerBound: ScType, upperBound: ScType): ScExistentialType = {
    ScExistentialType(ScTypeVariable(name), List(ScExistentialArgument(name, args, lowerBound, upperBound)))
  }

  def apply(quantified : ScType,
            wildcards : List[ScExistentialArgument]): ScExistentialType = {
    val result = new ScExistentialType(quantified, wildcards)
    ScType.allTypesCache.intern(result).asInstanceOf[ScExistentialType]
  }

  def unapply(e: ScExistentialType): Option[(ScType, List[ScExistentialArgument])] = {
    Some(e.quantified, e.wildcards)
  }
}

class ScExistentialArgument private (val name : String, val args : List[ScTypeParameterType],
                                     val lowerBound : ScType, val upperBound : ScType) {
  override def equals(other: scala.Any): Boolean = other match {
    case e: ScExistentialArgument =>
      e.name == name &&
        e.args == args &&
        e.lowerBound == lowerBound &&
        e.upperBound == upperBound
    case _ => false
  }

  override val hashCode: Int = name.hashCode + (args.hashCode() + (lowerBound.hashCode() + upperBound.hashCode() * 31) * 31) * 31

  override def toString: String = s"ScExistentialArgument($name, $args, $lowerBound, $upperBound)"

  def unpack = ScSkolemizedType(name, args, lowerBound, upperBound)

  def withoutAbstracts: ScExistentialArgument = ScExistentialArgument(name, args, lowerBound.removeAbstracts, upperBound.removeAbstracts)

  def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScExistentialArgument = {
    ScExistentialArgument(name, args, lowerBound.recursiveUpdate(update, visited), upperBound.recursiveUpdate(update, visited))
  }

  def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScExistentialArgument = {
    ScExistentialArgument(name, args, lowerBound.recursiveVarianceUpdateModifiable(data, update, -variance),
      upperBound.recursiveVarianceUpdateModifiable(data, update, variance))
  }

  def equivInner(exist: ScExistentialArgument, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    val s = (exist.args zip args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._1.name, ""), p._2)}
    val t = Equivalence.equivInner(lowerBound, s.subst(exist.lowerBound), undefinedSubst, falseUndef)
    if (!t._1) return (false, undefinedSubst)
    undefinedSubst = t._2
    Equivalence.equivInner(upperBound, s.subst(exist.upperBound), undefinedSubst, falseUndef)
  }

  def subst(substitutor: ScSubstitutor): ScExistentialArgument = {
    ScExistentialArgument(name, args.map(t => substitutor.subst(t).asInstanceOf[ScTypeParameterType]),
      substitutor subst lowerBound, substitutor subst upperBound)
  }
}

object ScExistentialArgument {
  private val argumentsCache = new Interner[ScExistentialArgument]()

  def apply(name: String, args: List[ScTypeParameterType], lowerBound: ScType, upperBound: ScType): ScExistentialArgument = {
    val result = new ScExistentialArgument(name, args, lowerBound, upperBound)
    argumentsCache.intern(result)
  }

  def unapply(e: ScExistentialArgument): Option[(String, List[ScTypeParameterType], ScType, ScType)] = {
    Some(e.name, e.args, e.lowerBound, e.upperBound)
  }
}

class ScSkolemizedType private (val name : String, val args : List[ScTypeParameterType], val lower : ScType, val upper : ScType)
  extends ValueType {
  override def equals(other: scala.Any): Boolean = other match {
    case s: ScSkolemizedType =>
      s.name == name &&
      s.args == args &&
      s.lower == lower &&
      s.upper == upper
    case _ => false
  }

  override val hashCode: Int = 12 + name.hashCode + (args.hashCode() + (lower.hashCode() + 31 * upper.hashCode()) * 31) * 31  

  override def toString: String = s"ScSkolemizedType($name, $args, $lower, $upper)"

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitSkolemizedType(this)
  }

  override def removeAbstracts = ScSkolemizedType(name, args, lower.removeAbstracts, upper.removeAbstracts)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        ScSkolemizedType(name, args, lower.recursiveUpdate(update, newVisited), upper.recursiveUpdate(update, newVisited))
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScSkolemizedType(name, args, lower.recursiveVarianceUpdateModifiable(newData, update, -variance),
          upper.recursiveVarianceUpdateModifiable(newData, update, variance))
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var u = uSubst
    r match {
      case ScSkolemizedType(rname, rargs, rlower, rupper) =>
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

object ScSkolemizedType {
  def apply(name: String, args: List[ScTypeParameterType], lower: ScType, upper: ScType): ScSkolemizedType = {
    val result = new ScSkolemizedType(name, args, lower, upper)
    ScType.allTypesCache.intern(result).asInstanceOf[ScSkolemizedType]
  }

  def unapply(e: ScSkolemizedType): Option[(String, List[ScTypeParameterType], ScType, ScType)] = {
    Some(e.name, e.args, e.lower, e.upper)
  }
}
