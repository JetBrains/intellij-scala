package org.jetbrains.plugins.scala
package lang
package psi
package types

import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import nonvalue.{ScTypePolymorphicType, NonValueType, ScMethodType}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.Misc._
import api.statements._
import params._
import api.toplevel.typedef.ScTypeDefinition
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.scala.collection.immutable.HashSet

import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import collection.Seq
import collection.mutable.{MultiMap, HashMap}
import lang.resolve.processor.{BaseProcessor, CompoundTypeCheckProcessor, ResolveProcessor}
import result.{TypingContext, TypeResult}
import api.base.patterns.ScBindingPattern
import api.base.ScFieldId
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import api.toplevel.{ScTypeParametersOwner, ScNamedElement, ScTypedDefinition}

object Conformance {
  case class AliasType(ta: ScTypeAlias, lower: TypeResult[ScType], upper: TypeResult[ScType])
  def isAliasType(tp: ScType): Option[AliasType] = {
    tp match {
      case ScDesignatorType(ta: ScTypeAlias) => Some(AliasType(ta, ta.lowerBound, ta.upperBound))
      case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAlias] =>
        val ta: ScTypeAlias = p.actualElement.asInstanceOf[ScTypeAlias]
        val subst: ScSubstitutor = p.actualSubst
        Some(AliasType(ta, ta.lowerBound.map(subst.subst(_)), ta.upperBound.map(subst.subst(_))))
      case ScParameterizedType(ScDesignatorType(ta: ScTypeAlias), args) => {
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(ta.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        Some(AliasType(ta, ta.lowerBound.map(genericSubst.subst(_)), ta.upperBound.map(genericSubst.subst(_))))
      }
      case ScParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAlias] => {
        val ta: ScTypeAlias = p.actualElement.asInstanceOf[ScTypeAlias]
        val subst: ScSubstitutor = p.actualSubst
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(ta.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        val s = subst.followed(genericSubst)
        Some(AliasType(ta, ta.lowerBound.map(s.subst(_)), ta.upperBound.map(s.subst(_))))
      }
      case _ => None
    }
  }

  /**
   * Checks, whether the following assignment is correct:
   * val x: l = (y: r)
   */
  def conforms(l: ScType, r: ScType, checkWeak: Boolean = false): Boolean =
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, false, checkWeak)._1

  def undefinedSubst(l: ScType, r: ScType, checkWeak: Boolean = false): ScUndefinedSubstitutor =
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, false, checkWeak)._2

  def conformsInner(l: ScType, r: ScType, visited: Set[PsiClass], subst: ScUndefinedSubstitutor,
                            noBaseTypes: Boolean = false, //todo: remove, not used
                            checkWeak: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled

    var undefinedSubst: ScUndefinedSubstitutor = subst

    if (checkWeak) {
      (r, l) match {
        case (Byte, Short | Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Short, Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Char, Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Int, Long | Float | Double) => return (true, undefinedSubst)
        case (Long, Float | Double) => return (true, undefinedSubst)
        case (Float, Double) => return (true, undefinedSubst)
        case _ =>
      }
    }
    (l, r) match {
      case (ScAbstractType(_, lower, upper), right) =>
//        return conformsInner(right, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
        return conformsInner(upper, right, visited, undefinedSubst, noBaseTypes, checkWeak)
      case (left, ScAbstractType(_, lower, upper)) =>
//        return conformsInner(left, upper, visited, undefinedSubst, noBaseTypes, checkWeak)
        return conformsInner(left, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
      case (u1: ScUndefinedType, u2: ScUndefinedType) if u2.level > u1.level =>
        return (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u1))
      case (u2: ScUndefinedType, u1: ScUndefinedType) if u2.level > u1.level =>
        return (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u1))
      case (u1: ScUndefinedType, u2: ScUndefinedType) if u2.level == u1.level => return (true, undefinedSubst)
      case (u: ScUndefinedType, tp: ScType) => return (true, undefinedSubst.addLower((u.tpt.name, u.tpt.getId), tp))
      case (tp: ScType, u: ScUndefinedType) => return (true, undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), tp))
      case _ => {
        val isEquiv = Equivalence.equivInner(l, r, undefinedSubst)
        if (isEquiv._1) return isEquiv
      }
    }

    def checkParameterizedType(parametersIterator: Iterator[PsiTypeParameter], args1: scala.Seq[ScType],
                               args2: scala.Seq[ScType]): (Boolean, ScUndefinedSubstitutor) = {
      val args1Iterator = args1.iterator
      val args2Iterator = args2.iterator
      while (parametersIterator.hasNext && args1Iterator.hasNext && args2Iterator.hasNext) {
        val tp = parametersIterator.next
        val argsPair = (args1Iterator.next, args2Iterator.next)
        tp match {
          case scp: ScTypeParam if (scp.isContravariant) => {
            val y = Conformance.conformsInner(argsPair._2, argsPair._1, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case scp: ScTypeParam if (scp.isCovariant) => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          //this case filter out such cases like undefined type
          case _ => {
            argsPair match {
              case (ScAbstractType(_, lower, upper), right) => {
                var t = conformsInner(upper, right, visited, undefinedSubst, noBaseTypes, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = conformsInner(right, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
              }
              case (left, ScAbstractType(_, lower, upper)) => {
                var t = conformsInner(upper, left, visited, undefinedSubst, noBaseTypes, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = conformsInner(left, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
              }
              case (u: ScUndefinedType, rt) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
              }
              case (lt, u: ScUndefinedType) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
              }
              case (_: ScExistentialArgument, _) => {
                val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                if (!y._1) return (false, undefinedSubst)
                else undefinedSubst = y._2
              }
              case (tp, _) if isAliasType(tp) != None && isAliasType(tp).get.ta.isExistentialTypeAlias => {
                val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                if (!y._1) return (false, undefinedSubst)
                else undefinedSubst = y._2
              }
              case _ => {
                val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
              }
            }
          }
        }
      }
      return (true, undefinedSubst)
    }
    (l, r) match {
      case (ScMethodType(returnType1, params1, _), ScMethodType(returnType2, params2, _)) => {
        if (params1.length != params2.length) return (false, undefinedSubst)
        var t = conformsInner(returnType1, returnType2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        var i = 0
        while (i < params1.length) {
          if (params1(i).isRepeated != params2(i).isRepeated) return (false, undefinedSubst)
          t = Equivalence.equivInner(params1(i).paramType, params2(i).paramType, undefinedSubst, false)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
      }
      case (ScTypePolymorphicType(internalType1, typeParameters1), ScTypePolymorphicType(internalType2, typeParameters2)) => {
        if (typeParameters1.length != typeParameters2.length) return (false, undefinedSubst)
        var i = 0
        while (i < typeParameters1.length) {
          var t = conformsInner(typeParameters1(i).lowerType, typeParameters2(i).lowerType, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          t = conformsInner(typeParameters2(i).upperType, typeParameters1(i).lowerType, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        import Suspension._
        val subst = new ScSubstitutor(new collection.immutable.HashMap[(String, String), ScType] ++ typeParameters1.zip(typeParameters2).map({
          tuple => ((tuple._1.name, ScalaPsiUtil.getPsiElementId(tuple._1.ptp)),
                  new ScTypeParameterType(tuple._2.name,
            tuple._2.ptp match {
              case p: ScTypeParam => p.typeParameters.toList.map{new ScTypeParameterType(_, ScSubstitutor.empty)}
              case _ => Nil
            }, tuple._2.lowerType, tuple._2.upperType, tuple._2.ptp))
        }), Map.empty, None)
        val t = conformsInner(subst.subst(internalType1), internalType2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        (true, undefinedSubst)
      }
      case (_: NonValueType, _) => return (false, undefinedSubst)
      case (_, _: NonValueType) => return (false, undefinedSubst)
      case (Any, _) => return (true, undefinedSubst)
      case (_, Nothing) => return (true, undefinedSubst)
      /*
        this case for checking: val x: T = null
        This is good if T class type: T <: AnyRef and !(T <: NotNull)
       */
      case (_, Null) => {
        if (!conforms(AnyRef, l)) return (false, undefinedSubst)
        ScType.extractDesignated(l) match {
          case Some((el, _)) => {
            val notNullClass = JavaPsiFacade.getInstance(el.getProject).findClass("scala.NotNull", el.getResolveScope)
            val notNullType = ScDesignatorType(notNullClass)
            return (!conforms(notNullType, l), undefinedSubst) //todo: think about undefinedSubst
          }
          case _ => return (true, undefinedSubst)
        }
      }
      case (tpt1: ScTypeParameterType, tpt2: ScTypeParameterType) => {
        val res = conformsInner(tpt1.lower.v, r, HashSet.empty, undefinedSubst)
        if (res._1) return res
        return conformsInner(l, tpt2.upper.v, HashSet.empty, undefinedSubst)
      }
      case (tpt: ScTypeParameterType, _) => return conformsInner(tpt.lower.v, r, HashSet.empty, undefinedSubst)
      case (_, tpt: ScTypeParameterType) => return conformsInner(l, tpt.upper.v, HashSet.empty, undefinedSubst)
      case (Null, _) => return (r == Nothing, undefinedSubst)
      case (AnyRef, Any) => return (false, undefinedSubst)
      case (AnyRef, AnyVal) => return (false, undefinedSubst)
      case (AnyRef, _: ValType) => return (false, undefinedSubst)
      case (AnyRef, t) if !t.isInstanceOf[ScExistentialType] &&
              !t.isInstanceOf[ScExistentialArgument] => return (true, undefinedSubst)
      case (Singleton, _) => return (false, undefinedSubst)
      case (AnyVal, _: ValType) => return (true, undefinedSubst)
      case (AnyVal, _) => return (false, undefinedSubst)
      case (_: ValType, _: ValType) => return (false, undefinedSubst)
      case (ScTupleType(comps1: Seq[ScType]), ScTupleType(comps2: Seq[ScType])) => {
        if (comps1.length != comps2.length) return (false, undefinedSubst)
        var i = 0
        while (i < comps1.length) {
          val comp1 = comps1(i)
          val comp2 = comps2(i)
          val t = conformsInner(comp1, comp2, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          else undefinedSubst = t._2
          i = i + 1
        }
        return (true, undefinedSubst)
      }
      case (fun: ScFunctionType, _) => {
        fun.resolveFunctionTrait match {
          case Some(tp) => return conformsInner(tp, r, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (_, fun: ScFunctionType) => {
        fun.resolveFunctionTrait match {
          case Some(tp) => return conformsInner(l, tp, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (tuple: ScTupleType, _) => {
        tuple.resolveTupleTrait match {
          case Some(tp) => return conformsInner(tp, r, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (_, tuple: ScTupleType) => {
        tuple.resolveTupleTrait match {
          case Some(tp) => return conformsInner(l, tp, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (ScThisType(clazz), _) => {
        return conformsInner(clazz.getTypeWithProjections(TypingContext.empty).getOrElse(return (false, undefinedSubst)), r, visited, subst, noBaseTypes)
      }
      case (_, ScThisType(clazz)) => {
        return conformsInner(l, clazz.getTypeWithProjections(TypingContext.empty).getOrElse(return (false, undefinedSubst)), visited, subst, noBaseTypes)
      }
      case (_, ScDesignatorType(v: ScBindingPattern)) => {
        return conformsInner(l, v.getType(TypingContext.empty).getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
      }
      case (_, ScDesignatorType(v: ScParameter)) => {
        return conformsInner(l, v.getType(TypingContext.empty).getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
      }
      case (_, ScDesignatorType(v: ScFieldId)) => {
        return conformsInner(l, v.getType(TypingContext.empty).getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
      }
      case (ScParameterizedType(ScProjectionType(projected, a: ScTypeAlias, subst), args), _) => {
        val lBound = subst.subst(a.lowerBound.getOrElse(return (false, undefinedSubst)))
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        val s = subst.followed(genericSubst)
        return conformsInner(s.subst(lBound), r, visited, undefinedSubst)
      }
      case (_, ScParameterizedType(proj@ScProjectionType(projected, _, _), args)) if proj.actualElement.isInstanceOf[ScTypeAlias] => {
        val a = proj.actualElement.asInstanceOf[ScTypeAlias]
        val subst = proj.actualSubst
        val uBound = subst.subst(a.upperBound.getOrElse(return (false, undefinedSubst)))
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        val s = subst.followed(genericSubst)
        return conformsInner(l, s.subst(uBound), visited, undefinedSubst)
      }
      case (ScParameterizedType(ScDesignatorType(a: ScTypeAlias), args), _) => {
        if (!a.isExistentialTypeAlias) {
          val lBound = a.lowerBound.getOrElse(return (false, undefinedSubst))
          val genericSubst = ScalaPsiUtil.
                  typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
          return conformsInner(genericSubst.subst(lBound), r, visited, undefinedSubst)
        }
        else {
          val lBound = a.lowerBound.getOrElse(return (false, undefinedSubst))
          val uBound = a.upperBound.getOrElse(return (false, undefinedSubst))
          val genericSubst = ScalaPsiUtil.
                  typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
          val t = conformsInner(genericSubst.subst(uBound), r, visited, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          return conformsInner(r, genericSubst.subst(lBound), visited, undefinedSubst)
        }
      }
      case (_, ScParameterizedType(ScDesignatorType(a: ScTypeAlias), args)) => {
        val uBound = a.upperBound.getOrElse(return (false, undefinedSubst))
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        return conformsInner(l, genericSubst.subst(uBound), visited, undefinedSubst)
      }
      case (JavaArrayType(arg1), JavaArrayType(arg2)) => {
        val argsPair = (arg1, arg2)
        argsPair match {
          case (ScAbstractType(_, lower, upper), right) => {
            var t = conformsInner(upper, right, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(right, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
          case (left, ScAbstractType(_, lower, upper)) => {
            var t = conformsInner(upper, left, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(left, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
          case (u: ScUndefinedType, rt) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
          }
          case (lt, u: ScUndefinedType) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
          }
          case (_: ScExistentialArgument, _) => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case (tp, _) if isAliasType(tp) != None && isAliasType(tp).get.ta.isExistentialTypeAlias => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case _ => {
            val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
        }
        return (true, undefinedSubst)
      }
      case (JavaArrayType(arg), ScParameterizedType(des, args)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        val argsPair = (arg, args(0))
        argsPair match {
          case (ScAbstractType(_, lower, upper), right) => {
            var t = conformsInner(upper, right, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(right, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
          case (left, ScAbstractType(_, lower, upper)) => {
            var t = conformsInner(upper, left, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(left, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
          case (u: ScUndefinedType, rt) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
          }
          case (lt, u: ScUndefinedType) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
          }
          case (_: ScExistentialArgument, _) => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case (tp, _) if isAliasType(tp) != None && isAliasType(tp).get.ta.isExistentialTypeAlias => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case _ => {
            val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
        }
        return (true, undefinedSubst)
      }
      case (ScParameterizedType(des, args), JavaArrayType(arg)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        val argsPair = (arg, args(0))
        argsPair match {
          case (ScAbstractType(_, lower, upper), right) => {
            var t = conformsInner(upper, right, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(right, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
          case (left, ScAbstractType(_, lower, upper)) => {
            var t = conformsInner(upper, left, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(left, lower, visited, undefinedSubst, noBaseTypes, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
          case (u: ScUndefinedType, rt) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
          }
          case (lt, u: ScUndefinedType) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
          }
          case (_: ScExistentialArgument, _) => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case (tp, _) if isAliasType(tp) != None && isAliasType(tp).get.ta.isExistentialTypeAlias => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case _ => {
            val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst, false)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
        }
        return (true, undefinedSubst)
      }
      case (ScParameterizedType(owner1: ScTypeParameterType, args1), ScParameterizedType(owner2: ScTypeParameterType, args2)) => {
        if (owner1 equiv owner2) {
          if (args1.length != args2.length) return (false, undefinedSubst)
          return checkParameterizedType(owner1.args.map(_.param).iterator, args1, args2)
        } else {
          return (false, undefinedSubst)
        }
      }
      case (ScParameterizedType(owner: ScUndefinedType, args1), ScParameterizedType(owner1: ScType, args2)) => {
        undefinedSubst = undefinedSubst.addLower((owner.tpt.name, owner.tpt.getId), r)
        if (args1.length != args2.length) return (false, undefinedSubst)
        return checkParameterizedType(owner.tpt.args.map(_.param).iterator, args1, args2)
      }
      case (ScParameterizedType(owner1: ScType, args1), ScParameterizedType(owner: ScUndefinedType, args2)) => {
        undefinedSubst = undefinedSubst.addUpper((owner.tpt.name, owner.tpt.getId), l)
        if (args1.length != args2.length) return (false, undefinedSubst)
        return checkParameterizedType(owner.tpt.args.map(_.param).iterator, args1, args2)
      }
      case (ScParameterizedType(owner: ScType, args1), ScParameterizedType(owner1: ScType, args2))
        if owner.equiv(owner1) => {
        if (args1.length != args2.length) return (false, undefinedSubst)
        ScType.extractClass(owner) match {
          case Some(owner) => {
            val parametersIterator = owner match {
              case td: ScTypeDefinition => td.typeParameters.iterator
              case _ => owner.getTypeParameters.iterator
            }
            return checkParameterizedType(parametersIterator, args1, args2)
          }
          case _ => return (false, undefinedSubst)
        }
      }
      case (ScParameterizedType(proj1@ScProjectionType(p1, elem1, subst1), args1),
            ScParameterizedType(proj2@ScProjectionType(p2, elem2, subst2), args2))
            if ScEquivalenceUtil.smartEquivalence(proj1.actualElement, proj2.actualElement)=> {
        val t = conformsInner(proj1, proj2, visited, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        if (args1.length != args2.length) return (false, undefinedSubst)
        val parametersIterator = proj1.actualElement match {
          case td: ScTypeParametersOwner => td.typeParameters.iterator
          case td: PsiTypeParameterListOwner => td.getTypeParameters.iterator
          case _ => return (false, undefinedSubst)
        }
        return checkParameterizedType(parametersIterator, args1, args2)
      }

      case (ScDesignatorType(a: ScTypeAlias), _) => {
        if (!a.isExistentialTypeAlias)
          return conformsInner(a.lowerBound.getOrElse(return (false, undefinedSubst)), r, visited, undefinedSubst)
        else {
          val t = conformsInner(a.upperBound.getOrElse(return (false, undefinedSubst)), r, visited, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          return conformsInner(r, a.lowerBound.getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
        }
      }
      case (_, ScDesignatorType(a: ScTypeAlias)) => {
        return conformsInner(l, a.upperBound.getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
      }
      /*If T<:Ui for i=1,...,n and for every binding d of a type or value x in R there exists a member binding
       of x in T which subsumes d, then T conforms to the compound type	U1	with	. . .	with	Un	{R }.

       U1	with	. . .	with	Un	{R } === t1
       T                             === t2
       U1	with	. . .	with	Un       === comps1
       Un                            === compn
       */
      case (t1@ScCompoundType(comps, decls, typeMembers, subst), t2) => {
        def workWith(t: ScNamedElement): Boolean = {
          val processor = new CompoundTypeCheckProcessor(t, undefinedSubst, subst)
          processor.processType(t2, t)
          undefinedSubst = processor.getUndefinedSubstitutor
          processor.getResult
        }
        return (comps.forall(comp => {
          val t = conformsInner(comp, t2, HashSet.empty, undefinedSubst)
          undefinedSubst = t._2
          t._1
        }) && decls.forall(decl => {
          decl match {
            case fun: ScFunction => workWith(fun)
            case v: ScValue => v.declaredElements forall (decl => workWith(decl))
            case v: ScVariable => v.declaredElements forall (decl => workWith(decl))
          }
        }) && typeMembers.forall(typeMember => {
          workWith(typeMember)
        }), undefinedSubst)
      }

      case (ScSkolemizedType(_, _, lower, _), _) => return conformsInner(lower, r, HashSet.empty, undefinedSubst)
      case (proj@ScProjectionType(projected, _, _), _) if proj.actualElement.isInstanceOf[ScTypeAlias] => {
        val ta = proj.actualElement.asInstanceOf[ScTypeAlias]
        val subst = proj.actualSubst
        if (!ta.isExistentialTypeAlias) {
          val lower = ta.lowerBound.getOrElse(return (false, undefinedSubst))
          return conformsInner(subst.subst(lower), r, visited, undefinedSubst)
        } else {
          val lower = ta.lowerBound.getOrElse(return (false, undefinedSubst))
          val upper = ta.upperBound.getOrElse(return (false, undefinedSubst))
          val t = conformsInner(subst.subst(upper), r, visited, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          return conformsInner(r, subst.subst(lower), visited, undefinedSubst)
        }
      }
      case (ScExistentialArgument(_, params1, lower1, upper1), ScExistentialArgument(_, params2, lower2, upper2))
        if params1.isEmpty && params2.isEmpty => {
        var t = conformsInner(upper1, upper2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        t = conformsInner(lower2, lower1, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        return (true, undefinedSubst)
      }
      case (ScExistentialArgument(_, params, lower, upper), _) if params.isEmpty => return conformsInner(upper, r, HashSet.empty, undefinedSubst)
      case (ex@ScExistentialType(q, wilds), _) => return conformsInner(q, r, HashSet.empty, undefinedSubst)
      case (_, ScSkolemizedType(_, _, _, upper)) => return conformsInner(l, upper, HashSet.empty, undefinedSubst)
      case (_, ScCompoundType(comps, _, _, _)) => {
        val iterator = comps.iterator
        while (iterator.hasNext) {
          val comp = iterator.next
          val t = conformsInner(l, comp, HashSet.empty, undefinedSubst)
          if (t._1) return (true, t._2)
        }
        return (false, undefinedSubst)
      }
      case (_, ScExistentialArgument(_, params, _, upper)) if params.isEmpty => return conformsInner(l, upper, HashSet.empty, undefinedSubst)
      case (_, ex: ScExistentialType) => return conformsInner(l, ex.skolem, HashSet.empty, undefinedSubst)
      case (_, proj@ScProjectionType(projected, _, _)) if proj.actualElement.isInstanceOf[ScTypeAlias] => {
        val ta = proj.actualElement.asInstanceOf[ScTypeAlias]
        val subst = proj.actualSubst
        val uBound = subst.subst(ta.upperBound.getOrElse(return (false, undefinedSubst)))
        return conformsInner(l, uBound, visited, undefinedSubst)
      }
      case (proj1@ScProjectionType(projected1, elem1, subst1), proj2@ScProjectionType(projected2, elem2, subst2))
        if ScEquivalenceUtil.smartEquivalence(proj1.actualElement, proj2.actualElement) => {
        return conformsInner(projected1, projected2, visited, undefinedSubst)
      }
      case (_, proj: ScProjectionType) => {
        proj.element match {
          case syntheticClass: ScSyntheticClass => return conformsInner(l, syntheticClass.t, HashSet.empty, undefinedSubst)
          case _ =>
        }
      }
      case _ =>
    }
    if (noBaseTypes) return (false, undefinedSubst)
    ScType.extractClassType(r) match {
      case Some((clazz: PsiClass, _)) if visited.contains(clazz) => return (false, undefinedSubst)
      case Some((rClass: PsiClass, subst: ScSubstitutor)) => {
        ScType.extractClass(l) match {
          case Some(lClass) => {
            if (rClass.getQualifiedName == "java.lang.Object" ) {
              return conformsInner(l, AnyRef, visited, undefinedSubst, noBaseTypes)
            } else if (lClass.getQualifiedName == "java.lang.Object") {
              return conformsInner(AnyRef, r, visited, undefinedSubst, noBaseTypes)
            }
            val inh = smartIsInheritor(rClass, subst, lClass)
            if (!inh._1) return (false, undefinedSubst)
            val tp = inh._2
            //Special case for higher kind types passed to generics.
            if (lClass.hasTypeParameters) {
              l match {
                case p: ScParameterizedType =>
                case f: ScFunctionType =>
                case t: ScTupleType =>
                case _ => return (true, undefinedSubst)
              }
            }
            val t = conformsInner(l, tp, visited + rClass, undefinedSubst, true)
            if (t._1) return (true, t._2)
            else return (false, undefinedSubst)
          }
          case _ => return (false, undefinedSubst)
        }
      }
      case _ => {
        val bases: Seq[ScType] = BaseTypes.get(r)
        val iterator = bases.iterator
        while (iterator.hasNext) {
          ProgressManager.checkCanceled
          val tp = iterator.next
          val t = conformsInner(l, tp, visited, undefinedSubst, true)
          if (t._1) return (true, t._2)
        }
        return (false, undefinedSubst)
      }//return BaseTypes.get(r).find {t => conforms(l, t, visited)}
    }
  }

  def getSignatureMapInner(clazz: PsiClass): HashMap[Signature, ScType] = {
    val m = new HashMap[Signature, ScType]
    val iterator = TypeDefinitionMembers.getSignatures(clazz).iterator
    while (iterator.hasNext) {
      val (full, _) = iterator.next
      m += ((full.sig, full.retType.v))
    }
    m
  }

  def getSignatureMap(clazz: PsiClass): HashMap[Signature, ScType] = {
    CachesUtil.get(
      clazz, CachesUtil.SIGNATURES_MAP_KEY,
      new CachesUtil.MyProvider(clazz, {clazz: PsiClass => getSignatureMapInner(clazz)})
        (PsiModificationTracker.MODIFICATION_COUNT)
      )
  }


  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass) : (Boolean, ScType) = {
    smartIsInheritor(leftClass, substitutor, rightClass, new collection.mutable.HashSet[PsiClass])
  }
  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass,
                               visited: collection.mutable.HashSet[PsiClass]): (Boolean, ScType) = {
    ProgressManager.checkCanceled
    val bases: Seq[Any] = leftClass match {
      case td: ScTypeDefinition => td.superTypes
      case _ => leftClass.getSuperTypes
    }
    val iterator = bases.iterator
    var res: ScType = null
    while (iterator.hasNext) {
      val tp: ScType = iterator.next match {
        case tp: ScType => substitutor.subst(tp)
        case pct: PsiClassType => substitutor.subst(ScType.create(pct, leftClass.getProject))
      }
      ScType.extractClassType(tp) match {
        case Some((clazz: PsiClass, _)) if visited.contains(clazz) =>
        case Some((clazz: PsiClass, subst)) if ScEquivalenceUtil.areClassesEquivalent(clazz, rightClass) => {
          visited += clazz
          if (res == null) res = tp
          else if (tp.conforms(res)) res = tp
        }
        case Some((clazz: PsiClass, subst)) => {
          visited += clazz
          val recursive = smartIsInheritor(clazz, subst, rightClass, visited)
          if (recursive._1) {
            if (res == null) res = recursive._2
            else if (recursive._2.conforms(res)) res = recursive._2
          }
        }
        case _ =>
      }
    }
    if (res == null) (false, null)
    else (true, res)
  }
}