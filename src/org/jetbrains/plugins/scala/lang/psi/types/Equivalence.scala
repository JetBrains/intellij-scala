package org.jetbrains.plugins.scala.lang.psi.types

import nonvalue.{ScTypePolymorphicType, ScMethodType}
import org.jetbrains.plugins.scala.Suspension
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi.{PsiNamedElement, PsiClass}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScThisReference, ScSuperReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement, ScPathElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.04.2010
 */

object Equivalence {
  def equiv(l: ScType, r: ScType): Boolean =
    equivInner(l, r, new ScUndefinedSubstitutor)._1

  def undefinedSubst(l: ScType, r: ScType): ScUndefinedSubstitutor =
    equivInner(l, r, new ScUndefinedSubstitutor)._2

  def equivInner(l: ScType, r: ScType, subst: ScUndefinedSubstitutor): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = subst

    (l, r) match {
      case (l: StdType, r: StdType) => (l == r, undefinedSubst)
      case (AnyRef, r) => {
        ScType.extractClass(r) match {
          case Some(clazz) if clazz.getQualifiedName == "java.lang.Object" => (true, undefinedSubst)
          case _ => (false, undefinedSubst)
        }
      }
      case (r, AnyRef) => equivInner(r, l, undefinedSubst)
      case (p: ScProjectionType, t: StdType) => {
        p.element match {
          case Some(synth: ScSyntheticClass) => equivInner(synth.t, t, undefinedSubst)
          case _ => (false, undefinedSubst)
        }
      }
      case (t: StdType, p: ScProjectionType) => equivInner(r, l, undefinedSubst)
      case (l@ScCompoundType(components, decls, typeDecls, subst), r: ScCompoundType) => {
        val list = components.zip(r.components)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next
          val t = equivInner(w1, w2, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }

        if (l.signatureMap.size != r.signatureMap.size) return (false, undefinedSubst)
        
        val iterator2 = l.signatureMap.elements
        while (iterator2.hasNext) {
          val (sig, t) = iterator2.next
          r.signatureMap.get(sig) match {
            case None => false
            case Some(t1) => {
              val f = equivInner(t, t1, undefinedSubst)
              if (!f._1) return (false, undefinedSubst)
              undefinedSubst = f._2
            }
          }
        }

        val types1 = l.types
        val subst1 = l.subst
        val types2 = r.types
        val subst2 = r.subst
        if (types1.size != l.types.size) return (false, undefinedSubst)
        else {
          for ((name, bounds1) <- types1) {
            types2.get(name) match {
              case None => return (false, undefinedSubst)
              case Some (bounds2) => {
                var t = equivInner(subst1.subst(bounds1._1), subst2.subst(bounds2._1), undefinedSubst)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = equivInner(subst1.subst(bounds1._2), subst2.subst(bounds2._2), undefinedSubst)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
              }
            }
          }
          (true, undefinedSubst)
        }
      }
      case (l@ScExistentialType(quantified, wildcards), ex : ScExistentialType) => {
        val unify = (ex.boundNames zip wildcards).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._1, ""), p._2)}
        val list = wildcards.zip(ex.wildcards)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next
          val t = equivInner(w1, unify.subst(w2), undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        equivInner(l.substitutor.subst(quantified), ex.substitutor.subst(ex.quantified), undefinedSubst)
      }
      case (l@ScExistentialArgument(name, args, lowerBound, upperBound), exist : ScExistentialArgument) => {
        val s = (exist.args zip args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._1.name, ""), p._2)}
        val t = equivInner(lowerBound, s.subst(exist.lowerBound), undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        equivInner(upperBound, s.subst(exist.upperBound), undefinedSubst)
      }
      case (ScFunctionType(returnType, params), ScFunctionType(rt1, params1)) => {
        if (params1.length != params.length) return (false, undefinedSubst)
        var t = equivInner(returnType, rt1, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        val iter1 = params.iterator
        val iter2 = params1.iterator
        while (iter1.hasNext) {
          t = equivInner(iter1.next, iter2.next, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        (true, undefinedSubst)
      }
      case (ScFunctionType(returnType, params), p: ScParameterizedType) => {
        p.getFunctionType match {
          case Some(function) => equivInner(l, function, undefinedSubst)
          case _ => (false, undefinedSubst)
        }
      }
      case (p: ScParameterizedType, ScFunctionType(returnType, params)) => equivInner(r, l, undefinedSubst)
      case (ScTupleType(components), ScTupleType(c1)) if c1.length == components.length => {
        val iter1 = components.iterator
        val iter2 = c1.iterator
        while (iter1.hasNext) {
          val t = equivInner(iter1.next, iter2.next, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        (true, undefinedSubst)
      }
      case (ScTupleType(components), p: ScParameterizedType) => {
        p.getTupleType match {
          case Some(tuple) => equivInner(l, tuple, undefinedSubst)
          case _ => (false, undefinedSubst)
        }
      }
      case (p: ScParameterizedType, ScTupleType(components)) => equivInner(r, l, undefinedSubst)
      case (ScDesignatorType(element), ScDesignatorType(element1)) => (element == element1, undefinedSubst)
      case (ScDesignatorType(element), ScSingletonType(path: ScPathElement)) => path match {
        case ref: ScStableCodeReferenceElement => {
          ref.bind match {
            case Some(ScalaResolveResult(el: PsiNamedElement, _)) => (el == element, undefinedSubst)
            case _ => (false, undefinedSubst)
          }
        }
        case _ => (false, undefinedSubst)
      }
      case (ScSingletonType(path: ScPathElement), ScDesignatorType(element)) => equivInner(r, l, undefinedSubst)
      case (JavaArrayType(arg), JavaArrayType(arg2)) => equivInner (arg, arg2, undefinedSubst)
      case (JavaArrayType(arg), ScParameterizedType(des, args)) if args.length == 1 => {
        ScType.extractClass(des) match {
          case Some(td) if td.getQualifiedName == "scala.Array" => equivInner(arg,args(0), undefinedSubst)
          case _ => (false, undefinedSubst)
        }
      }
      case (p: ScParameterizedType, j: JavaArrayType) => equivInner(r, l, undefinedSubst)
      case (ScParameterizedType(designator, typeArgs), ScParameterizedType(designator1, typeArgs1)) => {
        var t = equivInner(designator, designator1, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        if (typeArgs.length != typeArgs1.length) return (false, undefinedSubst)
        val iterator1 = typeArgs.iterator
        val iterator2 = typeArgs1.iterator
        while (iterator1.hasNext && iterator2.hasNext) {
          t = equivInner(iterator1.next, iterator2.next, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        return (true, undefinedSubst)
      }
      case (l@ScTypeConstructorType(alias, args, aliased), tct : ScTypeConstructorType) => {
        if (alias != tct.alias) return (false, undefinedSubst)
        val s = args.zip(tct.args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._2.name, p._2.getId), p._1)}
        val t = equivInner(l.lower.v, s.subst(tct.lower.v), undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        equivInner(l.upper.v, s.subst(tct.upper.v), undefinedSubst)
      }
      case (ScTypeAliasType(alias, args, lower, upper), tat : ScTypeAliasType) => {
        if (alias != tat.alias) return (false, undefinedSubst)

        val s = args.zip(tat.args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._2.name, p._2.getId), p._1)}

        (CyclicHelper.compute(alias, tat.alias)(() => {
          val t = equivInner(lower.v, s.subst(tat.lower.v), undefinedSubst)
          if (!t._1) (false, undefinedSubst)
          else {
            undefinedSubst = t._2
            equivInner(upper.v, s.subst(tat.upper.v), undefinedSubst)
          }
        }) match {
          case None => (true, undefinedSubst)
          case Some(b) => b
        })
      }
      case (ScTypeParameterType(name, args, lower, upper, param), stp: ScTypeParameterType) => {
        if (r eq this) return (true, undefinedSubst)
        (CyclicHelper.compute(param, stp.param)(() => {
          val t = equivInner(lower.v, stp.lower.v, undefinedSubst)
          if (!t._1) (false, undefinedSubst)
          else {
            undefinedSubst = t._2
            equivInner(upper.v, stp.upper.v, undefinedSubst)
          }
        }) match {
          case None => (true, undefinedSubst)
          case Some(b) => b
        })
      }
      case (ScProjectionType(projected, ref), ScProjectionType(p1, ref1)) => {
        if (ref1.refName != ref.refName) return (false, undefinedSubst)
        equivInner(projected, p1, undefinedSubst)
      }
      case (l@ScProjectionType(projected, ref), ScDesignatorType(des)) => projected match {
        case ScSingletonType(path) => {
          l.resolveResult match {
            case Some(ScalaResolveResult(el: PsiNamedElement, _)) => {
              (el == des, undefinedSubst)
            }
            case _ => (false, undefinedSubst)
          }
        }
        case ScDesignatorType(_) => l.resolveResult match {
          case Some(ScalaResolveResult(el: PsiNamedElement, _)) => (el == des, undefinedSubst)
          case _ => (false, undefinedSubst)
        }
        case _ => (false, undefinedSubst)
      }
      case (d: ScDesignatorType, p: ScProjectionType) => equivInner(r, l, undefinedSubst)
      case (l@ScProjectionType(projected, ref), ScSingletonType(path: ScPathElement)) => path match {
        case ref: ScStableCodeReferenceElement => {
          ref.bind match {
            case Some(ScalaResolveResult(el, _)) => {
              l.resolveResult match {
                case Some(ScalaResolveResult(el2, _)) => (el2 == el, undefinedSubst)
                case _ => (false, undefinedSubst)
              }
            }
            case _ => (false, undefinedSubst)
          }
        }
        case _ => (false, undefinedSubst)
      }
      case (s: ScSingletonType, p: ScProjectionType) => equivInner(r, l, undefinedSubst)
      case (ScSingletonType(path), ScSingletonType(path1)) => {
        def equiv(e1: ScPathElement, e2: ScPathElement): Boolean = {
          (e1, e2) match {
            case (r1: ScReferenceElement, r2: ScReferenceElement) =>
              (r1.resolve, r2.resolve) match {
                case (null, _) => false
                case (_, null) => false
                case (p1, p2) => p1 == p2
              }
            case (t1: ScThisReference, t2: ScThisReference) => t1.refTemplate == t2.refTemplate
            case (s1: ScSuperReference, s2: ScSuperReference) => s1.drvTemplate == s2.drvTemplate
            case _ => false
          }
        }
        (equiv(path, path1), undefinedSubst)
      }
      case (ScMethodType(returnType, params, ismplicit), m: ScMethodType) => {
        if (m.params.length != params.length) return (false, undefinedSubst)
        var t = equivInner(m.returnType, returnType,undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        for (i <- 0 until params.length) {
          //todo: Seq[Type] instead of Type*
          if (params(i).isRepeated != m.params(i).isRepeated) return (false, undefinedSubst)
          t = equivInner(params(i).paramType, m.params(i).paramType, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        return (true, undefinedSubst)
      }
      case (ScTypePolymorphicType(internalType, typeParameters), p: ScTypePolymorphicType) => {
        if (typeParameters.length != p.typeParameters.length) return (false, undefinedSubst)
        for (i <- 0 until typeParameters.length) {
          var t = equivInner(typeParameters(i).lowerType, p.typeParameters(i).lowerType, undefinedSubst)
          if (!t._1) return (false,undefinedSubst)
          undefinedSubst = t._2
          t = equivInner(typeParameters(i).upperType, p.typeParameters(i).upperType, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        import Suspension._
        val subst = new ScSubstitutor(new collection.immutable.HashMap[(String, String), ScType] ++
                typeParameters.zip(p.typeParameters).map({
          tuple => ((tuple._1.name, ScalaPsiUtil.getPsiElementId(tuple._1.ptp)), new ScTypeParameterType(tuple._2.name,
            List.empty, tuple._2.lowerType, tuple._2.upperType, tuple._2.ptp))
        }), Map.empty, Map.empty)
        equivInner(subst.subst(internalType), p.internalType, undefinedSubst)
      }
      case _ => (false, undefinedSubst)
    }
  }
}