package org.jetbrains.plugins.scala
package lang
package psi
package types

/**
 * @author ilyas
 */

import api.statements.{ScTypeAliasDefinition, ScTypeAlias}
import api.toplevel.{ScTypeParametersOwner}
import com.intellij.psi.{PsiTypeParameterListOwner, PsiNamedElement}
import api.statements.params.ScTypeParam
import nonvalue.NonValueType
import psi.impl.ScalaPsiManager
import result.TypingContext
import api.base.{ScStableCodeReferenceElement, ScPathElement}
import resolve.ScalaResolveResult

case class ScDesignatorType(val element: PsiNamedElement) extends ValueType {
  override def equiv(t: ScType) = t match {
    case ScDesignatorType(element1) => element eq element1
    case p : ScProjectionType => p equiv this
    case ScSingletonType(path: ScPathElement) => path match {
      case ref: ScStableCodeReferenceElement => {
        ref.bind match {
          case Some(ScalaResolveResult(el: PsiNamedElement, _)) => el == element
          case _ => false
        }
      }
      case _ => false
    }
    case AnyRef => t.equiv(this)
    case _ => false
  }
}

import _root_.scala.collection.immutable.{Map, HashMap}
import com.intellij.psi.{PsiTypeParameter, PsiClass}

case class ScParameterizedType(designator : ScType, typeArgs : Seq[ScType]) extends ValueType {
  def designated: Option[PsiNamedElement] = ScType.extractDesignated(designator) match {
    case Some((e, _)) => Some(e)
    case _ => None
  }

  lazy val substitutor : ScSubstitutor = {
    def forParams[T](paramsIterator: Iterator[T], initial: ScSubstitutor, map: T => ScTypeParameterType): ScSubstitutor = {
      var res = initial
      val argsIterator = typeArgs.iterator
      while (paramsIterator.hasNext && argsIterator.hasNext) {
        val p1 = map(paramsIterator.next)
        val p2 = argsIterator.next
        res = res bindT (p1.name, p2)
      }
      res
    }
    designator match {
      case ScPolymorphicType(_, args, _, _) => {
        forParams(args.iterator, ScSubstitutor.empty, (p: ScTypeParameterType) => p)
      }
      case _ => ScType.extractDesignated(designator) match {
        case Some((owner: ScTypeParametersOwner, s)) => {
          forParams(owner.typeParameters.iterator, s, (tp: ScTypeParam) => ScalaPsiManager.typeVariable(tp))
        }
        case Some((owner: PsiTypeParameterListOwner, s)) => {
          forParams(owner.getTypeParameters.iterator, s, (ptp: PsiTypeParameter) => ScalaPsiManager.typeVariable(ptp))
        }
        case _ => ScSubstitutor.empty
      }
    }
  }

  override def equiv(t: ScType): Boolean = t match {
    case ScParameterizedType(designator1, typeArgs1) => {
      if (!designator.equiv(designator1)) return false
      if (typeArgs.length != typeArgs1.length) return false
      val iterator1 = typeArgs.iterator
      val iterator2 = typeArgs1.iterator
      while (iterator1.hasNext && iterator2.hasNext) {
        if (!iterator1.next.equiv(iterator2.next)) return false
      }
      return true
    }
    case fun : ScFunctionType => fun equiv this
    case tuple : ScTupleType => tuple equiv this
    case _ => false
  }

  def getTupleType: Option[ScTupleType] = {
    ScType.extractClass(designator) match {
      case Some(clazz) if Option(clazz.getQualifiedName).exists(_.startsWith("scala.Tuple")) && typeArgs.length > 0 => {
        Some(new ScTupleType(typeArgs, clazz.getProject))
      }
      case _ => None
    }
  }

  def getFunctionType: Option[ScFunctionType] = {
    ScType.extractClass(designator) match {
      case Some(clazz) if clazz.getQualifiedName != null &&
              clazz.getQualifiedName.startsWith("scala.Function") && typeArgs.length > 0 => {
        Some(new ScFunctionType(typeArgs.apply(typeArgs.length - 1), typeArgs.slice(0, typeArgs.length - 1), clazz.getProject))
      }
      case _ => None
    }
  }
}

object ScParameterizedType {
  def create(c: PsiClass, s : ScSubstitutor) =
    new ScParameterizedType(new ScDesignatorType(c), collection.immutable.Seq(c.getTypeParameters.map({
      tp => s subst(ScalaPsiManager.typeVariable(tp))
    }).toSeq : _*))
}

abstract case class ScPolymorphicType(name : String, args : List[ScTypeParameterType],
                                     lower : Suspension[ScType], upper : Suspension[ScType]) extends ValueType

case class ScTypeConstructorType(alias : ScTypeAliasDefinition, override val args : List[ScTypeParameterType],
                                 aliased : Suspension[ScType])
extends ScPolymorphicType(alias.name, args, aliased, aliased) {
  override def equiv(t: ScType) = t match {
    case tct : ScTypeConstructorType => alias == tct.alias && {
      val s = args.zip(tct.args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._2.name, p._1)}
      lower.v.equiv(s.subst(tct.lower.v)) && upper.v.equiv(s.subst(tct.upper.v))
    }
    case _ => false
  }

  def this(tad : ScTypeAliasDefinition, s : ScSubstitutor) =
    this(tad, tad.typeParameters.toList.map{new ScTypeParameterType(_, s)},
      new Suspension[ScType]({() => s.subst(tad.aliasedType(TypingContext.empty).getOrElse(Any))}))
}

case class ScTypeAliasType(alias : ScTypeAlias, override val args : List[ScTypeParameterType],
                           override val lower : Suspension[ScType], override val upper : Suspension[ScType])
extends ScPolymorphicType(alias.name, args, lower, upper) {
    override def equiv(t: ScType) = t match {
      case tat : ScTypeAliasType => alias == tat.alias && {
        val s = args.zip(tat.args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._2.name, p._1)}
        (CyclicHelper.compute(alias, tat.alias)(() => lower.v.equiv(s.subst(tat.lower.v)) && upper.v.equiv(s.subst(tat.upper.v))) match {
          case None => true
          case Some(b) => b
        })
      }
      case _ => false
    }

  def this(ta : ScTypeAlias, s : ScSubstitutor) =
    this(ta, ta.typeParameters.toList.map{new ScTypeParameterType(_, s)},
      new Suspension[ScType]({() => s.subst(ta.lowerBound.getOrElse(Nothing))}),
      new Suspension[ScType]({() => s.subst(ta.upperBound.getOrElse(Any))}))
}

case class ScTypeParameterType(override val name: String, override val args: List[ScTypeParameterType],
                              override val lower: Suspension[ScType], override val upper: Suspension[ScType],
                              param: PsiTypeParameter)
extends ScPolymorphicType(name, args, lower, upper) {
  def this(tp : ScTypeParam, s : ScSubstitutor) =
    this(tp.name, tp.typeParameters.toList.map{new ScTypeParameterType(_, s)},
      new Suspension[ScType]({() => s.subst(tp.lowerBound.getOrElse(Nothing))}),
      new Suspension[ScType]({() => s.subst(tp.upperBound.getOrElse(Any))}),
      tp)

  def this(ptp: PsiTypeParameter, s: ScSubstitutor) =
    this(ptp.getName, ptp.getTypeParameters.toList.map(new ScTypeParameterType(_, s)),
      new Suspension[ScType]({() =>
              s.subst(
        ScCompoundType(collection.immutable.Seq(ptp.getExtendsListTypes.map(ScType.create(_, ptp.getProject)).toSeq ++
                ptp.getImplementsListTypes.map(ScType.create(_, ptp.getProject)).toSeq: _*), Seq.empty, Seq.empty, ScSubstitutor.empty))
      }),
      new Suspension[ScType]({() =>
              s.subst(
        ScCompoundType(ptp.getSuperTypes.map(ScType.create(_, ptp.getProject)).toSeq, Seq.empty, Seq.empty, ScSubstitutor.empty))
      }),
      ptp
    )

  override def equiv(t: ScType) = t match {
    case stp: ScTypeParameterType => (t eq this) ||
      (CyclicHelper.compute(param, stp.param)(() => lower.v.equiv(stp.lower.v) && upper.v.equiv(stp.upper.v)) match {
        case None => true
        case Some(b) => b
      })
    case _ => false
  }
}

case class ScUndefinedType(val tpt: ScTypeParameterType) extends NonValueType {
  var level = 0
  def this(tpt: ScTypeParameterType, level: Int) {
    this(tpt)
    this.level = level
  }
  override def equiv(t: ScType): Boolean = t.equiv(tpt)

  def inferValueType: ValueType = tpt
}

private[types] object CyclicHelper {
  private var pairs: List[(PsiNamedElement, PsiNamedElement)] = Nil

  def compute[R](pn1: PsiNamedElement, pn2: PsiNamedElement)(fun: () => R): Option[R] = {
    pairs.find(p => p._1 == pn1 && p._2 == pn2 || p._1 == pn2 && p._2 == pn1) match {
      case Some(_) => {
        pairs = Nil
        None
      }
      case None => {
        pairs = (pn1, pn2) :: pairs
        val res = Some(fun.apply)
        pairs = pairs - (pn1, pn2) 
        res
      }
    }
  }
}

case class ScTypeVariable(name: String) extends ValueType