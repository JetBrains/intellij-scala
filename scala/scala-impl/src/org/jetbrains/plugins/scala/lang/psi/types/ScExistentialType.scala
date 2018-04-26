package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util.Objects

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, TypeParamId}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{RecursiveUpdateException, ScSubstitutor, Update}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author ilyas
  */
class ScExistentialType private (val quantified: ScType,
                                 val wildcards: List[ScExistentialArgument],
                                 private val simplified: Option[ScType]) extends ScalaType with ValueType {

  override implicit def projectContext: ProjectContext = quantified.projectContext

  override protected def isAliasTypeInner: Option[AliasType] = {
    quantified.isAliasType.map(a => a.copy(lower = a.lower.map(_.unpackedType), upper = a.upper.map(_.unpackedType)))
  }

  def boundNames: List[String] = wildcards.map(_.name)

  override def removeAbstracts = ScExistentialType(quantified.removeAbstracts)

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScExistentialType = {
    try {
      ScExistentialType(quantified.recursiveUpdateImpl(updates, visited))
    } catch {
      case _: ClassCastException => throw new RecursiveUpdateException
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                    variance: Variance = Covariant, revertVariances: Boolean = false): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        try {
          ScExistentialType(quantified.recursiveVarianceUpdateModifiable(newData, update, variance))
        }
        catch {
          case _: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    if (r.equiv(Nothing)) return quantified.equiv(Nothing, uSubst)
    var undefinedSubst = uSubst
    val simplified = simplify()
    val conformance: ScalaConformance = typeSystem
    if (this != simplified) return simplified.equiv(r, undefinedSubst, falseUndef)
    (quantified, r) match {
      case (ParameterizedType(ScAbstractType(typeParameter, lowerBound, upperBound), args), _) if !falseUndef =>
        val subst = ScSubstitutor.bind(typeParameter.typeParameters, args)
        val upper: ScType =
          subst.subst(upperBound) match {
            case ParameterizedType(u, _) => ScExistentialType(ScParameterizedType(u, args))
            case u => ScExistentialType(ScParameterizedType(u, args))
          }
        val conformance = r.conforms(upper, undefinedSubst)
        if (!conformance._1) return conformance

        val lower: ScType =
          subst.subst(lowerBound) match {
            case ParameterizedType(l, _) => ScExistentialType(ScParameterizedType(l, args))
            case l => ScExistentialType(ScParameterizedType(l, args))
          }
        return lower.conforms(r, conformance._2)
      case (ParameterizedType(UndefinedType(typeParameter, _), args), _) if !falseUndef =>
        r match {
          case ParameterizedType(des, _) =>
            val y = conformance.addParam(typeParameter, des, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            undefinedSubst = y._2
            return ScExistentialType(ScParameterizedType(des, args)).equiv(r, undefinedSubst, falseUndef)
          case ScExistentialType(ParameterizedType(des, _), _) =>
            val y = conformance.addParam(typeParameter, des, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            undefinedSubst = y._2
            return ScExistentialType(ScParameterizedType(des, args)).equiv(r, undefinedSubst, falseUndef)
          case _ => return (false, undefinedSubst) //looks like something is wrong
        }
      case (ParameterizedType(pType, args), ParameterizedType(rType, _)) =>
        val res = pType.equivInner(rType, undefinedSubst, falseUndef)
        if (!res._1) return res
        conformance.extractParams(rType) match {
          case Some(iter) =>
            val (names, existArgsBounds) =
              args.zip(iter.toList).collect {
                case (arg: ScExistentialArgument, rParam: ScTypeParam)
                  if rParam.isCovariant && wildcards.contains(arg) => (arg.name, arg.upper)
                case (arg: ScExistentialArgument, rParam: ScTypeParam)
                  if rParam.isContravariant && wildcards.contains(arg) => (arg.name, arg.lower)
              }.unzip
            val subst = ScSubstitutor.bind(names, existArgsBounds)(TypeParamId.nameBased)
            return subst.subst(quantified).equiv(r, undefinedSubst, falseUndef)
          case _ =>
        }
      case _ =>
    }
    r.unpackedType match {
      case ex: ScExistentialType =>
        val simplified = ex.simplify()
        if (ex != simplified) return this.equiv(simplified, undefinedSubst, falseUndef)
        val list = wildcards.zip(ex.wildcards)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next()
          val t = w2.equivInner(w1, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        quantified.equiv(ex.quantified, undefinedSubst, falseUndef) //todo: probable problems with different positions of skolemized types.
      case poly: ScTypePolymorphicType if poly.typeParameters.length == wildcards.length =>
        val list = wildcards.zip(poly.typeParameters)
        val iterator = list.iterator
        var t = (true, undefinedSubst)
        while (iterator.hasNext) {
          val (w, tp) = iterator.next()
          t = w.lower.equivInner(tp.lowerType, t._2, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          t = w.upper.equivInner(tp.upperType, t._2, falseUndef)
          if (!t._1) return (false, undefinedSubst)
        }
        val polySubst = ScSubstitutor.bind(poly.typeParameters, wildcards)
        quantified.equiv(polySubst.subst(poly.internalType), t._2, falseUndef)
      case _ => (false, undefinedSubst)
    }
  }

  def simplify(): ScType = simplified.getOrElse(this)

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitExistentialType(this)
    case _ =>
  }

  override def typeDepth: Int = {
    def typeParamsDepth(typeParams: Seq[TypeParameterType]): Int = {
      typeParams.map {
        case typeParam =>
          val boundsDepth = typeParam.lowerType.typeDepth.max(typeParam.upperType.typeDepth)
          if (typeParam.arguments.nonEmpty) {
            (typeParamsDepth(typeParam.arguments) + 1).max(boundsDepth)
          } else boundsDepth
      }.max
    }

    val quantDepth = quantified.typeDepth
    if (wildcards.nonEmpty) {
      (wildcards.map {
        wildcard =>
          val boundsDepth = wildcard.lower.typeDepth.max(wildcard.upper.typeDepth)
          if (wildcard.args.nonEmpty) {
            (typeParamsDepth(wildcard.args) + 1).max(boundsDepth)
          } else boundsDepth
      }.max + 1).max(quantDepth)
    } else quantDepth
  }
}

object ScExistentialType {

  def unapply(existential: ScExistentialType): Option[(ScType, List[ScExistentialArgument])] =
    Some((existential.quantified, existential.wildcards))

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
    *
    * 1. and 2. are always true by construction.
    * Simplification by 3. and 4. is computed once when existential type is created.
    */
  final def apply(quantified: ScType): ScExistentialType = {
    quantified match {
      case e: ScExistentialType =>
        //first rule
        ScExistentialType(e.quantified)
      case _ =>
        //second rule
        val args = ScExistentialArgument.notBound(quantified).toList
        new ScExistentialType(quantified, args, simplify(quantified, args))
    }
  }

  private def simplify(quantified: ScType, wildcards: List[ScExistentialArgument]): Option[ScType] = {
    quantified match {
      case arg: ScExistentialArgument =>
        //shortcut for fourth rule, toplevel position is covariant
        return Some(ScExistentialType(arg.upper).simplify())
      case _ =>
    }

    //third rule
    if (wildcards.isEmpty) return Some(quantified)

    var updated = false
    //fourth rule
    val argsToBounds: (ScType, Variance) => (Boolean, ScType) = {
      case (ex: ScExistentialType, _) =>
        if (ex.simplified.nonEmpty) {
          updated = true
        }
        (true, ex.simplify())
      case (arg: ScExistentialArgument, variance) =>
        //fourth rule
        val argOrBound = variance match {
          case Covariant =>
            updated = true
            arg.upper
          case Contravariant =>
            updated = true
            arg.lower
          case _ =>
            arg
        }
        (true, argOrBound)
      case (tp, _) => (false, tp)
    }

    val simplifiedQ = quantified.recursiveVarianceUpdate(argsToBounds, Invariant)

    if (updated)
      Some(ScExistentialType(simplifiedQ).simplify())
    else None
  }
}

class ScExistentialArgument private (val name: String,
                                     val args: List[TypeParameterType],
                                     val lower: ScType,
                                     val upper: ScType,
                                     private val id: Int)
  extends NamedType with ValueType {

  override implicit def projectContext: ProjectContext = lower.projectContext

  override def hashCode(): Int =
    Objects.hash(name, args, lower, upper, id: Integer)

  override def equals(other: Any): Boolean = other match {
    case ex: ScExistentialArgument =>
      id == ex.id &&
        name == ex.name &&
        args == ex.args &&
        lower == ex.lower &&
        upper == ex.upper
    case _ => false
  }

  override def removeAbstracts: ScExistentialArgument = ScExistentialArgument(name, args, lower.removeAbstracts, upper.removeAbstracts, id)

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScExistentialArgument =
    ScExistentialArgument(name, args,
      lower.recursiveUpdateImpl(updates, visited),
      upper.recursiveUpdateImpl(updates, visited),
      id
    )

  def recursiveVarianceUpdateModifiableNoUpdate[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                            variance: Variance = Covariant): ScExistentialArgument =
    ScExistentialArgument(name, args,
      lower.recursiveVarianceUpdateModifiable(data, update, Contravariant),
      upper.recursiveVarianceUpdateModifiable(data, update, Covariant),
      id
    )

  def withBounds(newLower: ScType, newUpper: ScType): ScExistentialArgument =
    new ScExistentialArgument(name, args, newLower, newUpper, id)

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                    v: Variance = Covariant, revertVariances: Boolean = false): ScType = {
    update(this, v, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        recursiveVarianceUpdateModifiableNoUpdate(newData, update, v)
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case exist: ScExistentialArgument =>
        var undefinedSubst = uSubst
        val s = ScSubstitutor.bind(exist.args.map(_.name), args)(TypeParamId.nameBased)
        val t = lower.equiv(s.subst(exist.lower), undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        upper.equiv(s.subst(exist.upper), undefinedSubst, falseUndef)
      case _ => (false, uSubst)
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitExistentialArgument(this)
    case _ =>
  }
}

object ScExistentialArgument {
  def apply(name: String, args: List[TypeParameterType], lower: ScType, upper: ScType, id: Int) =
    new ScExistentialArgument(name, args, lower, upper, id)

  def apply(name: String, args: List[TypeParameterType], lower: ScType, upper: ScType, psi: PsiElement) =
    new ScExistentialArgument(name, args, lower, upper, id = psi.hashCode())

  def unapply(arg: ScExistentialArgument): Option[(String, List[TypeParameterType], ScType, ScType)] =
    Some((arg.name, arg.args, arg.lower, arg.upper))

  def notBound(tp: ScType): Set[ScExistentialArgument] = {
    var result: Set[ScExistentialArgument] = Set.empty
    tp.recursiveUpdate {
      case _: ScExistentialType => Stop //arguments inside are considered bound
      case arg: ScExistentialArgument =>
        result += arg
        ProcessSubtypes
      case _ =>
        ProcessSubtypes
    }
    result
  }

}
