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

  val substitutor : ScSubstitutor = {
    val (params, initial): (Seq[ScTypeParameterType], ScSubstitutor) = designator match {
      case ScPolymorphicType(_, args, _, _) => (args, ScSubstitutor.empty)
      case _ => ScType.extractDesignated(designator) match {
        case Some((owner: ScTypeParametersOwner, s)) => (owner.typeParameters.map{tp => ScalaPsiManager.typeVariable(tp)}, s)
        case Some((owner: PsiTypeParameterListOwner, s)) => (owner.getTypeParameters.map({tp => ScalaPsiManager.typeVariable(tp)}).toSeq, s)
        case _ => (Seq.empty, ScSubstitutor.empty)
      }
    }

    params match {
      case Seq() => initial
      case _ => {
        var res = initial
        for (p <- params.toArray zip typeArgs) {
          res = res bindT (p._1.name, p._2)
        }
        res
      }
    }
  }

  override def equiv(t: ScType): Boolean = t match {
    case ScParameterizedType(designator1, typeArgs1) => {
      return designator.equiv(designator1) &&
             (typeArgs.zip(typeArgs1) forall {case (x,y) => x equiv y})
    }
    case fun : ScFunctionType => fun equiv this
    case tuple : ScTupleType => tuple equiv this
    case _ => false
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
                ptp.getImplementsListTypes.map(ScType.create(_, ptp.getProject)).toSeq: _*), Seq.empty, Seq.empty))
      }),
      new Suspension[ScType]({() =>
              s.subst(
        ScCompoundType(ptp.getSuperTypes.map(ScType.create(_, ptp.getProject)).toSeq, Seq.empty, Seq.empty))
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