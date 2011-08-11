package org.jetbrains.plugins.scala
package lang
package psi
package types

import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import nonvalue.{ScTypePolymorphicType, NonValueType, ScMethodType}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import api.statements._
import params._
import api.toplevel.typedef.ScTypeDefinition
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.scala.collection.immutable.HashSet

import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import collection.Seq
import collection.mutable.HashMap
import lang.resolve.processor.CompoundTypeCheckProcessor
import result.{TypingContext, TypeResult}
import api.base.patterns.ScBindingPattern
import api.base.ScFieldId
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import api.toplevel.{ScTypeParametersOwner, ScNamedElement}

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
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, checkWeak)._1

  def undefinedSubst(l: ScType, r: ScType, checkWeak: Boolean = false): ScUndefinedSubstitutor =
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, checkWeak)._2

  def conformsInner(l: ScType, r: ScType, visited: Set[PsiClass], subst: ScUndefinedSubstitutor,
                            checkWeak: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    //todo: virtualize this code

    ProgressManager.checkCanceled()
    if (l.isInstanceOf[ScDesignatorType] && l.getValType != None) {
      return conformsInner(l.getValType.get, r, visited, subst, checkWeak)
    }
    if (r.isInstanceOf[ScDesignatorType] && r.getValType != None) {
      return conformsInner(l, r.getValType.get, visited, subst, checkWeak)
    }

    var undefinedSubst: ScUndefinedSubstitutor = subst
    def checkParameterizedType(parametersIterator: Iterator[PsiTypeParameter], args1: scala.Seq[ScType],
                               args2: scala.Seq[ScType]): (Boolean, ScUndefinedSubstitutor) = {
      val args1Iterator = args1.iterator
      val args2Iterator = args2.iterator
      while (parametersIterator.hasNext && args1Iterator.hasNext && args2Iterator.hasNext) {
        val tp = parametersIterator.next()
        val argsPair = (args1Iterator.next(), args2Iterator.next())
        tp match {
          case scp: ScTypeParam if (scp.isContravariant) => {
            //simplification rule 4 for existential types
            val r1 = argsPair._1 match {
              case ScExistentialArgument(_, List(), lower, upper) => lower
              case _ => argsPair._1
            }
            val y = Conformance.conformsInner(argsPair._2, r1, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case scp: ScTypeParam if (scp.isCovariant) => {
            //simplification rule 4 for existential types
            val r1 = argsPair._1 match {
              case ScExistentialArgument(_, List(), lower, upper) => upper
              case _ => argsPair._1
            }
            val y = Conformance.conformsInner(r1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          //this case filter out such cases like undefined type
          case _ => {
            argsPair match {
              case (u: ScUndefinedType, rt) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
              }
              case (lt, u: ScUndefinedType) => {
                undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
                undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
              }
              case (ScAbstractType(_, lower, upper), right) => {
                var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
              }
              case (left, ScAbstractType(_, lower, upper)) => {
                var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
              }
              case (_: ScExistentialArgument, _) => {
                val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                if (!y._1) return (false, undefinedSubst)
                else undefinedSubst = y._2
              }
              case (aliasType, _) if isAliasType(aliasType) != None && isAliasType(aliasType).get.ta.isExistentialTypeAlias => {
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
      (true, undefinedSubst)
    }

    if (checkWeak && r.isInstanceOf[ValType]) {
      (r, l) match {
        case (Byte, Short | Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Short, Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Char, Byte | Short | Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Int, Long | Float | Double) => return (true, undefinedSubst)
        case (Long, Float | Double) => return (true, undefinedSubst)
        case (Float, Double) => return (true, undefinedSubst)
        case _ =>
      }
    }

    if (l.isInstanceOf[ScUndefinedType]) {
      val u1 = l.asInstanceOf[ScUndefinedType]
      if (r.isInstanceOf[ScUndefinedType]) {
        val u2 = r.asInstanceOf[ScUndefinedType]
        if (u2.level > u1.level) {
          return (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u1))
        } else if (u1.level > u2.level) {
          return (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u1))
        } else {
          return (true, undefinedSubst)
        }
      } else {
        return (true, undefinedSubst.addLower((u1.tpt.name, u1.tpt.getId), r))
      }
    }
    if (r.isInstanceOf[ScUndefinedType]) {
      val u = r.asInstanceOf[ScUndefinedType]
      return (true, undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), l))
    }
    if (l.isInstanceOf[ScAbstractType]) {
      val a = l.asInstanceOf[ScAbstractType]
      return conformsInner(a.upper, r, visited, undefinedSubst, checkWeak)
    }
    if (r.isInstanceOf[ScAbstractType]) {
      val a = r.asInstanceOf[ScAbstractType]
      return conformsInner(l, a.lower, visited, undefinedSubst, checkWeak)
    }
    if (l.isInstanceOf[ScParameterizedType]) {
      val p = l.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScAbstractType]) {
        val a = p.designator.asInstanceOf[ScAbstractType]
        val upper = a.upper
        upper match {
          case ScParameterizedType(upper, _) =>
            return conformsInner(upper, r, visited, undefinedSubst, checkWeak)
          case _ =>
            return conformsInner(upper, r, visited, undefinedSubst, checkWeak)
        }
      }
    }
    if (r.isInstanceOf[ScParameterizedType]) {
      val p = r.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScAbstractType]) {
        val a = p.designator.asInstanceOf[ScAbstractType]
        val lower = a.lower
        lower match {
          case ScParameterizedType(lower, _) =>
            return conformsInner(l, lower, visited, undefinedSubst, checkWeak)
          case _ =>
            return conformsInner(l, lower, visited, undefinedSubst, checkWeak)
        }
      }
    }

    val isEquiv = Equivalence.equivInner(l, r, undefinedSubst)
    if (isEquiv._1) return isEquiv

    if (l.isInstanceOf[ScExistentialType]) {
      val simplified = l.asInstanceOf[ScExistentialType].simplify()
      if (simplified != l) return conformsInner(simplified, r, visited, undefinedSubst, checkWeak)
    }
    if (r.isInstanceOf[ScExistentialType]) {
      val simplified = r.asInstanceOf[ScExistentialType].simplify()
      if (simplified != r) return conformsInner(l, simplified, visited, undefinedSubst, checkWeak)
    }

    if (l.isInstanceOf[ScMethodType]) {
      if (r.isInstanceOf[ScMethodType]) {
        val m1 = l.asInstanceOf[ScMethodType]
        val m2 = r.asInstanceOf[ScMethodType]
        val params1 = m1.params
        val params2 = m2.params
        val returnType1 = m1.returnType
        val returnType2 = m2.returnType
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
        return (true, undefinedSubst)
      } else return (false, undefinedSubst)
    }

    if (l.isInstanceOf[ScTypePolymorphicType]) {
      if (r.isInstanceOf[ScTypePolymorphicType]) {
        val t1 = l.asInstanceOf[ScTypePolymorphicType]
        val t2 = r.asInstanceOf[ScTypePolymorphicType]
        val typeParameters1 = t1.typeParameters
        val typeParameters2 = t2.typeParameters
        val internalType1 = t1.internalType
        val internalType2 = t2.internalType
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
        return (true, undefinedSubst)
      } else {
        return (false, undefinedSubst)
      }
    }
    if (l.isInstanceOf[ScSkolemizedType]) {
      val s = l.asInstanceOf[ScSkolemizedType]
      return conformsInner(s.lower, r, HashSet.empty, undefinedSubst)
    }
    if (r.isInstanceOf[ScSkolemizedType]) {
      val s = r.asInstanceOf[ScSkolemizedType]
      return conformsInner(l, s.upper, HashSet.empty, undefinedSubst)
    }
    if (l.isInstanceOf[ScParameterizedType]) {
      val p = l.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScSkolemizedType]) {
        val s = p.designator.asInstanceOf[ScSkolemizedType]
        val lower = s.lower
        lower match {
          case ScParameterizedType(lower, _) =>
            return conformsInner(lower, r, visited, undefinedSubst, checkWeak)
          case _ =>
            return conformsInner(lower, r, visited, undefinedSubst, checkWeak)
        }
      }
    }
    if (r.isInstanceOf[ScParameterizedType]) {
      val p = r.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScSkolemizedType]) {
        val s = p.designator.asInstanceOf[ScSkolemizedType]
        val upper = s.upper
        upper match {
          case ScParameterizedType(upper, _) =>
            return conformsInner(l, upper, visited, undefinedSubst, checkWeak)
          case _ =>
            return conformsInner(l, upper, visited, undefinedSubst, checkWeak)
        }
      }
    }
    if (l.isInstanceOf[NonValueType]) return (false, undefinedSubst)
    if (r.isInstanceOf[NonValueType]) return (false, undefinedSubst)
    if (l eq Any) return (true, undefinedSubst)
    if (r eq Nothing) return (true, undefinedSubst)
    if (r eq Null) {
      /*
        this case for checking: val x: T = null
        This is good if T class type: T <: AnyRef and !(T <: NotNull)
       */
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

    if (l.isInstanceOf[ScTypeParameterType]) {
      if (r.isInstanceOf[ScTypeParameterType]) {
        val tpt1 = l.asInstanceOf[ScTypeParameterType]
        val tpt2 = r.asInstanceOf[ScTypeParameterType]
        val res = conformsInner(tpt1.lower.v, r, HashSet.empty, undefinedSubst)
        if (res._1) return res
        return conformsInner(l, tpt2.upper.v, HashSet.empty, undefinedSubst)
      }
    }

    if (l.isInstanceOf[ScExistentialArgument]) {
      val e = l.asInstanceOf[ScExistentialArgument]
      if (e.args.isEmpty) {
        return conformsInner(e.upperBound, r, HashSet.empty, undefinedSubst)
      }
    }
    if (r.isInstanceOf[ScExistentialArgument]) {
      val e = r.asInstanceOf[ScExistentialArgument]
      if (e.args.isEmpty) {
        return conformsInner(l, e.upperBound, HashSet.empty, undefinedSubst) //todo: that is strange, it's also upper (!)
      }
    }
    if (l.isInstanceOf[ScDesignatorType]) {
      //todo: duplicate, must be before tpt
      val des = l.asInstanceOf[ScDesignatorType]
      if (des.element.isInstanceOf[ScTypeAlias]) {
        val a = des.element.asInstanceOf[ScTypeAlias]
        if (a.isExistentialTypeAlias) {
          val t = conformsInner(a.upperBound.getOrElse(return (false, undefinedSubst)), r, visited, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          return conformsInner(r, a.lowerBound.getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
        }
      }
    }
    if (l.isInstanceOf[ScTypeParameterType]) {
      val tpt = l.asInstanceOf[ScTypeParameterType]
      return conformsInner(tpt.lower.v, r, HashSet.empty, undefinedSubst)
    }
    if (r.isInstanceOf[ScTypeParameterType]) {
      val tpt = r.asInstanceOf[ScTypeParameterType]
      return conformsInner(l, tpt.upper.v, HashSet.empty, undefinedSubst)
    }
    if (l eq Null) return (r == Nothing, undefinedSubst)

    if (l eq AnyRef) {
      if (r eq  Any) return (false, undefinedSubst)
      else if (r eq  AnyVal) return (false, undefinedSubst)
      else if (r.isInstanceOf[ValType]) return (false, undefinedSubst)
      else if (!r.isInstanceOf[ScExistentialArgument] && !r.isInstanceOf[ScExistentialType])
        return (true, undefinedSubst)
    }

    if (l eq Singleton) return (false, undefinedSubst)
    if (l eq AnyVal) {
      if (r.isInstanceOf[ValType]) return (true, undefinedSubst)
      else return (false, undefinedSubst)
    }
    if (l.isInstanceOf[ValType] && r.isInstanceOf[ValType]) return (false, undefinedSubst)

    if (l.isInstanceOf[ScTupleType]) {
      val t1 = l.asInstanceOf[ScTupleType]
      if (r.isInstanceOf[ScTupleType]) {
        val t2 = r.asInstanceOf[ScTupleType]
        val comps1 = t1.components
        val comps2 = t2.components
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
      } else {
        t1.resolveTupleTrait match {
          case Some(tp) => return conformsInner(tp, r, visited, subst, checkWeak)
          case _ => return (false, undefinedSubst)
        }
      }
    }
    if (r.isInstanceOf[ScTupleType]) {
      val t2 = r.asInstanceOf[ScTupleType]
      t2.resolveTupleTrait match {
        case Some(tp) => return conformsInner(l, tp, visited, subst, checkWeak)
        case _ => return (false, undefinedSubst)
      }
    }
    if (l.isInstanceOf[ScFunctionType]) {
      val f1 = l.asInstanceOf[ScFunctionType]
      f1.resolveFunctionTrait match {
        case Some(tp) => return conformsInner(tp, r, visited, subst, checkWeak)
        case _ => return (false, undefinedSubst)
      }
    }
    if (r.isInstanceOf[ScFunctionType]) {
      val f2 = r.asInstanceOf[ScFunctionType]
      f2.resolveFunctionTrait match {
        case Some(tp) => return conformsInner(l, tp, visited, subst, checkWeak)
        case _ => return (false, undefinedSubst)
      }
    }
    if (l.isInstanceOf[ScThisType]) {
      val t1 = l.asInstanceOf[ScThisType]
      val clazz = t1.clazz
      val result = clazz.getTypeWithProjections(TypingContext.empty)
      if (result.isEmpty) return (false, undefinedSubst)
      return conformsInner(result.get, r, visited, subst, checkWeak)
    }
    if (r.isInstanceOf[ScThisType]) {
      val t2 = r.asInstanceOf[ScThisType]
      val clazz = t2.clazz
      val result = clazz.getTypeWithProjections(TypingContext.empty)
      if (result.isEmpty) return (false, undefinedSubst)
      return conformsInner(l, result.get, visited, subst, checkWeak)
    }
    if (r.isInstanceOf[ScDesignatorType]) {
      val d = r.asInstanceOf[ScDesignatorType]
      d.element match {
        case v: ScBindingPattern => {
          val result = v.getType(TypingContext.empty)
          if (result.isEmpty) return (false, undefinedSubst)
          return conformsInner(l, result.get, visited, undefinedSubst)
        }
        case v: ScParameter => {
          val result = v.getType(TypingContext.empty)
          if (result.isEmpty) return (false, undefinedSubst)
          return conformsInner(l, result.get, visited, undefinedSubst)
        }
        case v: ScFieldId => {
          val result = v.getType(TypingContext.empty)
          if (result.isEmpty) return (false, undefinedSubst)
          return conformsInner(l, result.get, visited, undefinedSubst)
        }
        case _ =>
      }
    }

    if (l.isInstanceOf[ScParameterizedType]) {
      val p = l.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScProjectionType]) {
        val proj = p.designator.asInstanceOf[ScProjectionType]
        if (proj.actualElement.isInstanceOf[ScTypeAlias]) {
          val args = p.typeArgs
          val a = proj.actualElement.asInstanceOf[ScTypeAlias]
          val subst = proj.actualSubst
          val lBound = subst.subst(a.lowerBound.getOrElse(return (false, undefinedSubst)))
          val genericSubst = ScalaPsiUtil.
            typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
          val s = subst.followed(genericSubst)
          return conformsInner(s.subst(lBound), r, visited, undefinedSubst)
        }
      }
    }
    if (r.isInstanceOf[ScParameterizedType]) {
      val p = r.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScProjectionType]) {
        val proj = p.designator.asInstanceOf[ScProjectionType]
        if (proj.actualElement.isInstanceOf[ScTypeAlias]) {
          val args = p.typeArgs
          val a = proj.actualElement.asInstanceOf[ScTypeAlias]
          val subst = proj.actualSubst
          val uBound = subst.subst(a.upperBound.getOrElse(return (false, undefinedSubst)))
          val genericSubst = ScalaPsiUtil.
            typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
          val s = subst.followed(genericSubst)
          return conformsInner(l, s.subst(uBound), visited, undefinedSubst)
        }
      }
    }
    if (l.isInstanceOf[ScParameterizedType]) {
      val p = l.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScDesignatorType]) {
        val des = p.designator.asInstanceOf[ScDesignatorType]
        val args = p.typeArgs
        if (des.element.isInstanceOf[ScTypeAlias]) {
          val a = des.asInstanceOf[ScTypeAlias]
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
      }
    }
    if (r.isInstanceOf[ScParameterizedType]) {
      val p = r.asInstanceOf[ScParameterizedType]
      if (p.designator.isInstanceOf[ScDesignatorType]) {
        val des = p.designator.asInstanceOf[ScDesignatorType]
        if (des.element.isInstanceOf[ScTypeAlias]) {
          val a = des.element.asInstanceOf[ScTypeAlias]
          val args = p.typeArgs
          val uBound = a.upperBound.getOrElse(return (false, undefinedSubst))
          val genericSubst = ScalaPsiUtil.
            typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
          return conformsInner(l, genericSubst.subst(uBound), visited, undefinedSubst)
        }
      }
    }
    if (l.isInstanceOf[JavaArrayType]) {
      val a1 = l.asInstanceOf[JavaArrayType]
      if (r.isInstanceOf[JavaArrayType]) {
        val a2 = r.asInstanceOf[JavaArrayType]
        val arg1 = a1.arg
        val arg2 = a2.arg
        val argsPair = (arg1, arg2)
        argsPair match {
          case (ScAbstractType(_, lower, upper), right) => {
            var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
          case (left, ScAbstractType(_, lower, upper)) => {
            var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
            t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
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
      } else if (r.isInstanceOf[ScParameterizedType]) {
        val p2 = r.asInstanceOf[ScParameterizedType]
        val args = p2.typeArgs
        val des = p2.designator
        if (args.length == 1 && (ScType.extractClass(des) match {
          case Some(q) => q.getQualifiedName == "scala.Array"
          case _ => false
        })) {
          val arg = a1.arg
          val argsPair = (arg, args(0))
          argsPair match {
            case (ScAbstractType(_, lower, upper), right) => {
              var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
              t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
            }
            case (left, ScAbstractType(_, lower, upper)) => {
              var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
              t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
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
      }
    }

    if (r.isInstanceOf[JavaArrayType]) {
      if (l.isInstanceOf[ScParameterizedType]) {
        val p1 = l.asInstanceOf[ScParameterizedType]
        val args = p1.typeArgs
        val des = p1.designator
        if (args.length == 1 && (ScType.extractClass(des) match {
          case Some(q) => q.getQualifiedName == "scala.Array"
          case _ => false
        })) {
          val arg = r.asInstanceOf[JavaArrayType].arg
          val argsPair = (arg, args(0))
          argsPair match {
            case (ScAbstractType(_, lower, upper), right) => {
              var t = conformsInner(upper, right, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
              t = conformsInner(right, lower, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
            }
            case (left, ScAbstractType(_, lower, upper)) => {
              var t = conformsInner(upper, left, visited, undefinedSubst, checkWeak)
              if (!t._1) return (false, undefinedSubst)
              undefinedSubst = t._2
              t = conformsInner(left, lower, visited, undefinedSubst, checkWeak)
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
      }
    }

    if (l.isInstanceOf[ScParameterizedType]) {
      val p1 = l.asInstanceOf[ScParameterizedType]
      if (r.isInstanceOf[ScParameterizedType]) {
        val p2 = r.asInstanceOf[ScParameterizedType]
        val des1 = p1.designator
        val des2 = p2.designator
        val args1 = p1.typeArgs
        val args2 = p2.typeArgs
        if (des1.isInstanceOf[ScTypeParameterType] && des2.isInstanceOf[ScTypeParameterType]) {
          val owner1 = des1.asInstanceOf[ScTypeParameterType]
          if (des1 equiv des2) {
            if (args1.length != args2.length) return (false, undefinedSubst)
            return checkParameterizedType(owner1.args.map(_.param).iterator, args1, args2)
          } else {
            return (false, undefinedSubst)
          }
        } else if (des1.isInstanceOf[ScUndefinedType]) {
          val owner1 = des1.asInstanceOf[ScUndefinedType]
          val parameterType = owner1.tpt
          val anotherType = ScParameterizedType(des2, parameterType.args)
          undefinedSubst = undefinedSubst.addLower((owner1.tpt.name, owner1.tpt.getId), anotherType)
          if (args1.length != args2.length) return (false, undefinedSubst)
          return checkParameterizedType(owner1.tpt.args.map(_.param).iterator, args1, args2)
        } else if (des2.isInstanceOf[ScUndefinedType]) {
          val owner2 = des2.asInstanceOf[ScUndefinedType]
          val parameterType = owner2.tpt
          val anotherType = ScParameterizedType(des1, parameterType.args)
          undefinedSubst = undefinedSubst.addUpper((owner2.tpt.name, owner2.tpt.getId), anotherType)
          if (args1.length != args2.length) return (false, undefinedSubst)
          return checkParameterizedType(owner2.tpt.args.map(_.param).iterator, args1, args2)
        } else if (des1.equiv(des2)) {
          if (args1.length != args2.length) return (false, undefinedSubst)
          ScType.extractClass(des1) match {
            case Some(ownerClazz) => {
              val parametersIterator = ownerClazz match {
                case td: ScTypeDefinition => td.typeParameters.iterator
                case _ => ownerClazz.getTypeParameters.iterator
              }
              return checkParameterizedType(parametersIterator, args1, args2)
            }
            case _ => return (false, undefinedSubst)
          }
        } else {
          if (des1.isInstanceOf[ScProjectionType]) {
            val proj1 = des1.asInstanceOf[ScProjectionType]
            if (des2.isInstanceOf[ScProjectionType]) {
              val proj2 = des2.asInstanceOf[ScProjectionType]
              if (ScEquivalenceUtil.smartEquivalence(proj1.actualElement, proj2.actualElement)) {
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
            }
          }
        }
      }
    }

    if (l.isInstanceOf[ScDesignatorType]) {
      val des = l.asInstanceOf[ScDesignatorType]
      if (des.element.isInstanceOf[ScTypeAlias]) {
        val a = des.element.asInstanceOf[ScTypeAlias]
        if (!a.isExistentialTypeAlias)
          return conformsInner(a.lowerBound.getOrElse(return (false, undefinedSubst)), r, visited, undefinedSubst)
        else {
          val t = conformsInner(a.upperBound.getOrElse(return (false, undefinedSubst)), r, visited, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          return conformsInner(r, a.lowerBound.getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
        }
      }
    }

    if (r.isInstanceOf[ScDesignatorType]) {
      val des = r.asInstanceOf[ScDesignatorType]
      if (des.element.isInstanceOf[ScTypeAlias]) {
        val a = des.element.asInstanceOf[ScTypeAlias]
        return conformsInner(l, a.upperBound.getOrElse(return (false, undefinedSubst)), visited, undefinedSubst)
      }
    }

    /*If T<:Ui for i=1,...,n and for every binding d of a type or value x in R there exists a member binding
     of x in T which subsumes d, then T conforms to the compound type	U1	with	. . .	with	Un	{R }.

     U1	with	. . .	with	Un	{R } === t1
     T                             === t2
     U1	with	. . .	with	Un       === comps1
     Un                            === compn
     */
    if (l.isInstanceOf[ScCompoundType]) {
      val t1 = l.asInstanceOf[ScCompoundType]
      val comps = t1.components
      val decls = t1.decls
      val typeMembers = t1.typeDecls
      val compoundSubst = t1.subst
      def workWith(t: ScNamedElement): Boolean = {
        val processor = new CompoundTypeCheckProcessor(t, undefinedSubst, compoundSubst)
        processor.processType(r, t)
        undefinedSubst = processor.getUndefinedSubstitutor
        processor.getResult
      }
      return (comps.forall(comp => {
        val t = conformsInner(comp, r, HashSet.empty, undefinedSubst)
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

    if (l.isInstanceOf[ScCompoundType]) {
      val t1 = l.asInstanceOf[ScCompoundType]
      val comps = t1.components
      val compoundSubst = t1.subst
      val decls = t1.decls
      val typeMembers = t1.typeDecls
      def workWith(t: ScNamedElement): Boolean = {
        val processor = new CompoundTypeCheckProcessor(t, undefinedSubst, compoundSubst)
        processor.processType(r, t)
        undefinedSubst = processor.getUndefinedSubstitutor
        processor.getResult
      }
      return (comps.forall(comp => {
        val t = conformsInner(comp, r, HashSet.empty, undefinedSubst)
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

    if (l.isInstanceOf[ScProjectionType]) {
      val proj = l.asInstanceOf[ScProjectionType]
      if (proj.actualElement.isInstanceOf[ScTypeAlias]) {
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
    }

    if (l.isInstanceOf[ScExistentialArgument]) {
      if (r.isInstanceOf[ScExistentialArgument]) {
        val ex1 = l.asInstanceOf[ScExistentialArgument]
        val ex2 = r.asInstanceOf[ScExistentialArgument]
        val params1 = ex1.args
        val params2 = ex2.args
        if (params1.isEmpty && params2.isEmpty) {
          val upper1 = ex1.upperBound
          val upper2 = ex2.upperBound
          val lower1 = ex1.lowerBound
          val lower2 = ex2.lowerBound
          var t = conformsInner(upper1, upper2, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          t = conformsInner(lower2, lower1, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          return (true, undefinedSubst)
        }
      }
    }

    if (l.isInstanceOf[ScExistentialType]) {
      val q = l.asInstanceOf[ScExistentialType].quantified
      return conformsInner(q, r, HashSet.empty, undefinedSubst)
    }

    if (r.isInstanceOf[ScCompoundType]) {
      val comps = r.asInstanceOf[ScCompoundType].components
      val iterator = comps.iterator
      while (iterator.hasNext) {
        val comp = iterator.next()
        val t = conformsInner(l, comp, HashSet.empty, undefinedSubst)
        if (t._1) return (true, t._2)
      }
      return (false, undefinedSubst)
    }

    if (r.isInstanceOf[ScExistentialType]) {
      val ex = r.asInstanceOf[ScExistentialType]
      return conformsInner(l, ex.skolem, HashSet.empty, undefinedSubst)
    }

    if (r.isInstanceOf[ScProjectionType]) {
      val proj = r.asInstanceOf[ScProjectionType]
      if (proj.actualElement.isInstanceOf[ScTypeAlias]) {
        val ta = proj.actualElement.asInstanceOf[ScTypeAlias]
        val subst = proj.actualSubst
        val uBound = subst.subst(ta.upperBound.getOrElse(return (false, undefinedSubst)))
        return conformsInner(l, uBound, visited, undefinedSubst)
      }
    }

    if (r.isInstanceOf[ScProjectionType]) {
      val proj2 = r.asInstanceOf[ScProjectionType]
      if (l.isInstanceOf[ScProjectionType] &&
        ScEquivalenceUtil.smartEquivalence(l.asInstanceOf[ScProjectionType].actualElement, proj2.actualElement)) {
        val proj1 = l.asInstanceOf[ScProjectionType]
        val projected1 = proj1.projected
        val projected2 = proj2.projected
        return conformsInner(projected1, projected2, visited, undefinedSubst)
      } else {
        proj2.element match {
          case syntheticClass: ScSyntheticClass =>
            return conformsInner(l, syntheticClass.t, HashSet.empty, undefinedSubst)
          case _ =>
        }
      }
    }

    //tail, based on class inheritance
    ScType.extractClassType(r) match {
      case Some((clazz: PsiClass, _)) if visited.contains(clazz) => (false, undefinedSubst)
      case Some((rClass: PsiClass, subst: ScSubstitutor)) => {
        ScType.extractClass(l) match {
          case Some(lClass) => {
            if (rClass.getQualifiedName == "java.lang.Object" ) {
              return conformsInner(l, AnyRef, visited, undefinedSubst, checkWeak)
            } else if (lClass.getQualifiedName == "java.lang.Object") {
              return conformsInner(AnyRef, r, visited, undefinedSubst, checkWeak)
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
            if (t._1) (true, t._2)
            else (false, undefinedSubst)
          }
          case _ => (false, undefinedSubst)
        }
      }
      case _ => {
        val bases: Seq[ScType] = BaseTypes.get(r)
        val iterator = bases.iterator
        while (iterator.hasNext) {
          ProgressManager.checkCanceled()
          val tp = iterator.next()
          val t = conformsInner(l, tp, visited, undefinedSubst, true)
          if (t._1) return (true, t._2)
        }
        (false, undefinedSubst)
      }//return BaseTypes.get(r).find {t => conforms(l, t, visited)}
    }
  }

  def getSignatureMapInner(clazz: PsiClass): HashMap[Signature, ScType] = {
    val m = new HashMap[Signature, ScType]
    val iterator = TypeDefinitionMembers.getSignatures(clazz).iterator
    while (iterator.hasNext) {
      val (full, _) = iterator.next()
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
    if (!leftClass.isInheritor(rightClass, true)) return (false, null)
    smartIsInheritor(leftClass, substitutor, rightClass, new collection.mutable.HashSet[PsiClass])
  }
  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass,
                               visited: collection.mutable.HashSet[PsiClass]): (Boolean, ScType) = {
    ProgressManager.checkCanceled()
    val bases: Seq[Any] = leftClass match {
      case td: ScTypeDefinition => td.superTypes
      case _ => leftClass.getSuperTypes
    }
    val iterator = bases.iterator
    var res: ScType = null
    while (iterator.hasNext) {
      val tp: ScType = iterator.next() match {
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