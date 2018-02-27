package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, TypeParamId}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{RecursiveUpdateException, ScSubstitutor, Update}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * @author ilyas
  */
case class ScExistentialType(quantified: ScType,
                             wildcards: List[ScExistentialArgument]) extends ScalaType with ValueType {

  override implicit def projectContext: ProjectContext = quantified.projectContext

  override protected def isAliasTypeInner: Option[AliasType] = {
    quantified.isAliasType.map(a => a.copy(lower = a.lower.map(_.unpackedType), upper = a.upper.map(_.unpackedType)))
  }

  def boundNames: List[String] = wildcards.map(_.name)

  override def removeAbstracts = ScExistentialType(quantified.removeAbstracts,
    wildcards.map(_.withoutAbstracts))

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScExistentialType = {
    try {
      ScExistentialType(
        quantified.recursiveUpdateImpl(updates, visited),
        wildcards.map(_.updateSubtypes(updates, visited))
      )
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
          ScExistentialType(quantified.recursiveVarianceUpdateModifiable(newData, update, variance),
            wildcards.map(_.recursiveVarianceUpdateModifiableNoUpdate(newData, update, variance)))
        }
        catch {
          case _: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    if (r.equiv(Nothing)) return quantified.equiv(Nothing, uSubst)
    var undefinedSubst = uSubst
    val simplified = unpackedType match {
      case ex: ScExistentialType => ex.simplify()
      case u => u
    }
    val conformance: ScalaConformance = typeSystem
    if (this != simplified) return simplified.equiv(r, undefinedSubst, falseUndef)
    (quantified, r) match {
      case (ParameterizedType(ScAbstractType(parameterType, lowerBound, upperBound), args), _) if !falseUndef =>
        val subst = ScSubstitutor.bind(parameterType.arguments, args)
        val upper: ScType =
          subst.subst(upperBound) match {
            case ParameterizedType(u, _) => ScExistentialType(ScParameterizedType(u, args), wildcards)
            case u => ScExistentialType(ScParameterizedType(u, args), wildcards)
          }
        val conformance = r.conforms(upper, undefinedSubst)
        if (!conformance._1) return conformance

        val lower: ScType =
          subst.subst(lowerBound) match {
            case ParameterizedType(l, _) => ScExistentialType(ScParameterizedType(l, args), wildcards)
            case l => ScExistentialType(ScParameterizedType(l, args), wildcards)
          }
        return lower.conforms(r, conformance._2)
      case (ParameterizedType(UndefinedType(parameterType, _), args), _) if !falseUndef =>
        r match {
          case ParameterizedType(des, defArgs) =>
            val y = conformance.addParam(parameterType, des, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            undefinedSubst = y._2
            return ScExistentialType(ScParameterizedType(des, args), wildcards).equiv(r, undefinedSubst, falseUndef)
          case ScExistentialType(ParameterizedType(des, defArgs), _) =>
            val y = conformance.addParam(parameterType, des, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            undefinedSubst = y._2
            return ScExistentialType(ScParameterizedType(des, args), wildcards).equiv(r, undefinedSubst, falseUndef)
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

  def wildcardsMap(): mutable.HashMap[ScExistentialArgument, Seq[ScType]] = {
    val res = mutable.HashMap.empty[ScExistentialArgument, Seq[ScType]]
    //todo: use recursiveVarianceUpdateModifiable?
    def checkRecursive(tp: ScType, rejected: Set[String]) {
      ProgressManager.checkCanceled()
      tp match {
        case JavaArrayType(argument) => checkRecursive(argument, rejected)
        case ScAbstractType(tpt, lower, upper) =>
          checkRecursive(tpt, rejected)
          checkRecursive(lower, rejected)
          checkRecursive(upper, rejected)
        case ScCompoundType(comps, signatureMap, typeMap) =>
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
        case ex: ScExistentialType =>
          var newSet = if (ex ne this) rejected ++ ex.boundNames else rejected
          checkRecursive(ex.quantified, newSet)
          if (ex eq this) newSet = rejected ++ ex.boundNames
          ex.wildcards.foreach(ex => {
            checkRecursive(ex.lower, newSet)
            checkRecursive(ex.upper, newSet)
          })
        case ScProjectionType(projected, _) =>
          checkRecursive(projected, rejected)
        case ParameterizedType(designator, typeArgs) =>
          checkRecursive(designator, rejected)
          typeArgs.foreach(checkRecursive(_, rejected))
        case _: TypeParameterType =>
        case ScExistentialArgument(name, _, _, _) =>
          //todo: update args, lower, upper?
          wildcards.foreach(arg => if (arg.name == name && !rejected.contains(arg.name)) {
            res.update(arg, res.getOrElse(arg, Seq.empty[ScType]) ++ Seq(tp))
          })
        case UndefinedType(tpt, _) => checkRecursive(tpt, rejected)
        case ScMethodType(returnType, params, _) =>
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
    checkRecursive(this, Set.empty)
    wildcards.foreach {
      case ScExistentialArgument(_, _, lower, upper) =>
        checkRecursive(lower, Set.empty)
        checkRecursive(upper, Set.empty)
    }
    res
  }

  //todo: use recursiveVarianceUpdateModifiable?
  private def updateRecursive(tp: ScType, rejected: Set[String] = Set.empty, variance: Variance = Covariant)
                             (implicit update: (Variance, ScExistentialArgument, ScType) => ScType): ScType = {
    if (variance == Invariant) return tp //optimization
    tp match {
      case _: StdType => tp
      case ScCompoundType(components, signatureMap, typeMap) =>
        val newSet = rejected ++ typeMap.keys

        def updateTypeParam: TypeParameter => TypeParameter = {
          case TypeParameter(typeParameters, lowerType, upperType, psiTypeParameter) =>
            TypeParameter(typeParameters.map(updateTypeParam),
              updateRecursive(lowerType, newSet, variance),
              updateRecursive(upperType, newSet, -variance),
              psiTypeParameter)
        }

        new ScCompoundType(components, signatureMap.map {
          case (s, sctype) =>
            val pTypes: Seq[Seq[() => ScType]] =
              s.substitutedTypes.map(_.map(f => () => updateRecursive(f(), newSet, variance)))
            val tParams = s.typeParams.subst(updateTypeParam)
            val rt: ScType = updateRecursive(sctype, newSet, -variance)
            (new Signature(s.name, pTypes, tParams,
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
      case ScProjectionType(_, _) => tp
      case JavaArrayType(_) => tp
      case ParameterizedType(designator, typeArgs) =>
        val parameteresIterator = designator match {
          case tpt: TypeParameterType =>
            tpt.arguments.map(_.psiTypeParameter).iterator
          case undef: UndefinedType =>
            undef.parameterType.arguments.map(_.psiTypeParameter).iterator
          case tp: ScType =>
            tp.extractClass match {
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
        var newSet = if (ex ne this) rejected ++ ex.boundNames else rejected
        val q = updateRecursive(_quantified, newSet, variance)
        if (ex eq this) newSet = rejected ++ ex.boundNames
        ScExistentialType(q, _wildcards.map(arg => ScExistentialArgument(arg.name, arg.args.map(arg =>
          updateRecursive(arg, newSet, -variance).asInstanceOf[TypeParameterType]),
          updateRecursive(arg.lower, newSet, -variance), updateRecursive(arg.upper, newSet, variance))))
      case ScThisType(_) => tp
      case ScDesignatorType(_) => tp
      case _: TypeParameterType =>
        //should return TypeParameterType (for undefined type)
        tp
      case ScExistentialArgument(name, args, lower, upper) =>
        def res = ScExistentialArgument(name, args.map(arg =>
          updateRecursive(arg, rejected, -variance).asInstanceOf[TypeParameterType]),
          updateRecursive(lower, rejected, -variance),
          updateRecursive(upper, rejected, variance))
        if (!rejected.contains(name)) {
          wildcards.find(_.name == name) match {
            case Some(arg) => update(variance, arg, res)
            case _ => res
          }
        } else res
      case UndefinedType(tpt,_ ) => UndefinedType(
        updateRecursive(tpt, rejected, variance).asInstanceOf[TypeParameterType]
      )
      case m@ScMethodType(returnType, params, isImplicit) =>
        implicit val elementScope = m.elementScope
        ScMethodType(updateRecursive(returnType, rejected, variance),
          params.map(param => param.copy(paramType = updateRecursive(param.paramType, rejected, -variance))),
          isImplicit)
      case ScAbstractType(tpt, lower, upper) =>
        ScAbstractType(updateRecursive(tpt, rejected, variance).asInstanceOf[TypeParameterType],
          updateRecursive(lower, rejected, -variance),
          updateRecursive(upper, rejected, variance))
      case ScTypePolymorphicType(internalType, typeParameters) =>
        ScTypePolymorphicType(
          updateRecursive(internalType, rejected, variance),
          typeParameters.map {
            case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
              TypeParameter(parameters, // todo: is it important here to update?
                updateRecursive(lowerType, rejected, variance),
                updateRecursive(upperType, rejected, variance),
                psiTypeParameter)
          })
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
    def hasWildcards(tp: ScType): Boolean = tp.subtypeExists {
      case ScExistentialArgument(name, _, _, _) => wildcards.exists(_.name == name)
      case _ => false
    }

    val res = updateRecursive(this, Set.empty, Covariant) {
      case (variance, arg, tp) =>
        variance match {
          case Covariant if !hasWildcards(arg.upper)=>
            updated = true
            arg.upper
          case Contravariant if !hasWildcards(arg.lower)=>
            updated = true
            arg.lower
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
  def simpleExistential(name: String, args: List[TypeParameterType], lowerBound: ScType, upperBound: ScType): ScExistentialType = {
    val ex = ScExistentialArgument(name, args, lowerBound, upperBound)
    ScExistentialType(ex, List(ex))
  }

  def existingWildcards(tp: ScType): Set[String] = {
    val existingWildcards = new mutable.HashSet[String]
    tp.visitRecursively {
      case ex: ScExistentialType => existingWildcards ++= ex.boundNames
      case _ =>
    }
    existingWildcards.toSet
  }

  @tailrec
  def fixExistentialArgumentName(name: String, existingWildcards: Set[String]): String = {
    if (existingWildcards.contains(name)) {
      fixExistentialArgumentName(name + "$u", existingWildcards) //todo: fix it for name == "++"
    } else name
  }

  def fixExistentialArgumentNames(tp: ScType, existingWildcards: Set[String]): ScType = {
    if (existingWildcards.isEmpty) tp
    else {
      tp.recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
        case (s: ScExistentialArgument, _, data) if !data.contains(s.name) =>
          val name = fixExistentialArgumentName(s.name, existingWildcards)
          (true, ScExistentialArgument(name, s.args, s.lower, s.upper), data)
        case (ex: ScExistentialType, _, data) =>
          (false, ex, data ++ ex.boundNames)
        case (t, _, data) => (false, t, data)
      })
    }
  }
}

case class ScExistentialArgument(name: String, args: List[TypeParameterType], lower: ScType, upper: ScType)
  extends NamedType with ValueType {

  override implicit def projectContext: ProjectContext = lower.projectContext

  def withoutAbstracts: ScExistentialArgument = ScExistentialArgument(name, args, lower.removeAbstracts, upper.removeAbstracts)

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScExistentialArgument = {
    ScExistentialArgument(name, args,
      lower.recursiveUpdateImpl(updates, visited),
      upper.recursiveUpdateImpl(updates, visited))
  }

  def recursiveVarianceUpdateModifiableNoUpdate[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                            variance: Variance = Covariant): ScExistentialArgument = {
    ScExistentialArgument(name, args, lower.recursiveVarianceUpdateModifiable(data, update, Contravariant),
      upper.recursiveVarianceUpdateModifiable(data, update, Covariant))
  }

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
