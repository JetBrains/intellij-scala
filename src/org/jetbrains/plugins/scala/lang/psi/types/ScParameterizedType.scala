package org.jetbrains.plugins.scala
package lang
package psi
package types

/**
 * @author ilyas
 */

import java.util.Objects
import java.util.concurrent.ConcurrentMap

import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType, TypeVisitor, UndefinedType, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.collection.immutable.ListMap

class ScParameterizedType private(val designator: ScType, val typeArguments: Seq[ScType]) extends ParameterizedType with ScalaType {

  override protected def isAliasTypeInner: Option[AliasType] = {
    designator match {
      case ScDesignatorType(ta: ScTypeAlias) =>
        val existingWildcards = ta.lowerBound.map(ScExistentialType.existingWildcards).getOrElse(Set.empty) ++
          (if (ta.isDefinition) Set.empty else ta.upperBound.map(ScExistentialType.existingWildcards).getOrElse(Set.empty))

        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(_.nameAndId),
            typeArguments.map(ScExistentialType.fixExistentialArgumentNames(_, existingWildcards)))
        val lowerBound = ta.lowerBound.map(genericSubst.subst)
        val upperBound =
          if (ta.isDefinition) lowerBound
          else ta.upperBound.map(genericSubst.subst)
        Some(AliasType(ta, lowerBound, upperBound))
      case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAlias] =>
        val ta: ScTypeAlias = p.actualElement.asInstanceOf[ScTypeAlias]
        val subst: ScSubstitutor = p.actualSubst

        val existingWildcards = ta.lowerBound.map(subst.subst).map(ScExistentialType.existingWildcards).getOrElse(Set.empty) ++
          (if (ta.isDefinition) Set.empty else ta.upperBound.map(subst.subst).map(ScExistentialType.existingWildcards).getOrElse(Set.empty))

        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(_.nameAndId),
            typeArguments.map(ScExistentialType.fixExistentialArgumentNames(_, existingWildcards)))
        val s = subst.followed(genericSubst)
        val lowerBound = ta.lowerBound.map(s.subst)
        val upperBound =
          if (ta.isDefinition) lowerBound
          else ta.upperBound.map(s.subst)
        Some(AliasType(ta, lowerBound, upperBound))
      case _ => None
    }
  }

  private var hash: Int = -1

  //noinspection HashCodeUsesVar
  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(designator, typeArguments)

    hash
  }

  protected override def substitutorInner: ScSubstitutor = {
    def forParams[T](paramsIterator: Iterator[T], initial: ScSubstitutor, map: T => TypeParameterType): ScSubstitutor = {
      val argsIterator = typeArguments.iterator
      val builder = ListMap.newBuilder[(String, Long), ScType]
      while (paramsIterator.hasNext && argsIterator.hasNext) {
        val p1 = map(paramsIterator.next())
        val p2 = argsIterator.next()
        builder += ((p1.nameAndId, p2))
        //res = res bindT ((p1.name, p1.getId), p2)
      }
      val subst = ScSubstitutor(builder.result())
      initial followed subst
    }
    designator match {
      case TypeParameterType(args, _, _, _) =>
        forParams(args.iterator, ScSubstitutor.empty, (p: TypeParameterType) => p)
      case _ => designator.extractDesignatedType(expandAliases = false) match {
        case Some((owner: ScTypeParametersOwner, s)) =>
          forParams(owner.typeParameters.iterator, s, (typeParam: ScTypeParam) => TypeParameterType(typeParam, None))
        case Some((owner: PsiTypeParameterListOwner, s)) =>
          forParams(owner.getTypeParameters.iterator, s, (psiTypeParameter: PsiTypeParameter) => TypeParameterType(psiTypeParameter, None))
        case _ => ScSubstitutor.empty
      }
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        val des = designator.extractDesignated(expandAliases = false) match {
          case Some(n: ScTypeParametersOwner) =>
            n.typeParameters.map {
              case tp if tp.isContravariant => -1
              case tp if tp.isCovariant => 1
              case _ => 0
            }
          case _ => Seq.empty
        }
        ParameterizedType(designator.recursiveVarianceUpdateModifiable(newData, update, variance),
          typeArguments.zipWithIndex.map {
            case (ta, i) =>
              val v = if (i < des.length) des(i) else 0
              ta.recursiveVarianceUpdateModifiable(newData, update, v * variance)
          })
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    val Conformance: ScalaConformance = typeSystem
    val Nothing = projectContext.stdTypes.Nothing

    var undefinedSubst = uSubst
    (this, r) match {
      case (ParameterizedType(Nothing, _), Nothing) => (true, uSubst)
      case (ParameterizedType(Nothing, _), ParameterizedType(Nothing, _)) => (true, uSubst)
      case (ParameterizedType(ScAbstractType(tpt, lower, upper), args), _) =>
        if (falseUndef) return (false, uSubst)
        val subst = ScSubstitutor(tpt.arguments.zip(args).map {
          case (tpt: TypeParameterType, tp: ScType) => (tpt.nameAndId, tp)
        }.toMap)
        var conformance = r.conforms(subst.subst(upper), uSubst)
        if (!conformance._1) return (false, uSubst)
        conformance = subst.subst(lower).conforms(r, conformance._2)
        if (!conformance._1) return (false, uSubst)
        (true, conformance._2)
      case (ParameterizedType(proj@ScProjectionType(_, _, _), _), _) if proj.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            (lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }).equiv(r, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case (ParameterizedType(ScDesignatorType(_: ScTypeAliasDefinition), _), _) =>
        isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            (lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }).equiv(r, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case (ParameterizedType(UndefinedType(_, _), _), ParameterizedType(_, _)) =>
        val t = Conformance.processHigherKindedTypeParams(this, r.asInstanceOf[ParameterizedType], undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        (true, t._2)
      case (ParameterizedType(_, _), ParameterizedType(UndefinedType(_, _), _)) =>
        val t = Conformance.processHigherKindedTypeParams(r.asInstanceOf[ParameterizedType], this, undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        (true, t._2)
      case (ParameterizedType(_, _), ParameterizedType(designator1, typeArgs1)) =>
        var t = designator.equiv(designator1, undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        if (typeArguments.length != typeArgs1.length) return (false, undefinedSubst)
        val iterator1 = typeArguments.iterator
        val iterator2 = typeArgs1.iterator
        while (iterator1.hasNext && iterator2.hasNext) {
          t = iterator1.next().equiv(iterator2.next(), undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        (true, undefinedSubst)
      case _ => (false, undefinedSubst)
    }
  }

  /**
   * @return Some((designator, paramType, returnType)), or None
   */
  def getPartialFunctionType: Option[(ScType, ScType, ScType)] = {
    getStandardType("scala.PartialFunction") match {
      case Some((typeDef, Seq(param, ret))) => Some((ScDesignatorType(typeDef), param, ret))
      case None => None
    }
  }

  /**
   * @param  prefix of the qualified name of the type
   * @return (typeDef, typeArgs)
   */
  private def getStandardType(prefix: String): Option[(ScTypeDefinition, Seq[ScType])] = {
    def startsWith(clazz: PsiClass, qualNamePrefix: String) = clazz.qualifiedName != null && clazz.qualifiedName.startsWith(qualNamePrefix)

    designator.extractClassType match {
      case Some((clazz: ScTypeDefinition, sub)) if startsWith(clazz, prefix) =>
        val result = clazz.getType(TypingContext.empty)
        result match {
          case Success(t, _) =>
            val substituted = (sub followed substitutor).subst(t)
            substituted match {
              case pt: ScParameterizedType =>
                Some((clazz, pt.typeArguments))
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitParameterizedType(this)
    case _ =>
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[ScParameterizedType]

  override def equals(other: Any): Boolean = other match {
    case that: ScParameterizedType =>
      (that canEqual this) &&
        designator == that.designator &&
        typeArguments == that.typeArguments
    case _ => false
  }
}

object ScParameterizedType {

  val cache: ConcurrentMap[(ScType, Seq[ScType]), ValueType] =
    ContainerUtil.createConcurrentWeakMap[(ScType, Seq[ScType]), ValueType]()

  def apply(designator: ScType, typeArgs: Seq[ScType]): ValueType = {
    def createCompoundProjectionParameterized(pt: ScParameterizedType): ValueType = {
      pt.isAliasType match {
        case Some(AliasType(_: ScTypeAliasDefinition, _, Success(upper: ValueType, _))) => upper
        case _ => pt
      }
    }

    val simple = new ScParameterizedType(designator, typeArgs)
    designator match {
      case ScProjectionType(_: ScCompoundType, _, _) =>
        cache.atomicGetOrElseUpdate((designator, typeArgs),
          createCompoundProjectionParameterized(simple))
      case _ => simple
    }
  }
}
