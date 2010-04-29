package org.jetbrains.plugins.scala
package lang
package psi
package types

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import api.statements.params.ScTypeParam
import api.toplevel.typedef.{ScClass, ScTrait, ScTemplateDefinition, ScTypeDefinition}
import com.intellij.openapi.project.DumbService
import com.intellij.psi.{GenericsUtil, PsiClass}
import collection.mutable.{ArrayBuffer, Set, HashSet}
import api.statements.ScTypeAliasDefinition
import result.TypingContext

object Bounds {

  def glb(t1: ScType, t2: ScType) = {
    if (t1.conforms(t2)) t1
    else if (t2.conforms(t1)) t2
    else new ScCompoundType(Seq(t1, t2), Seq.empty, Seq.empty, ScSubstitutor.empty)
  }

  def lub(t1: ScType, t2: ScType): ScType = lub(t1, t2, 0)

  private def lub(t1: ScType, t2: ScType, depth : Int): ScType = {
    if (t1.conforms(t2)) t2
    else if (t2.conforms(t1)) t1
    else (t1, t2) match {
      case (fun@ScFunctionType(rt1, p1), ScFunctionType(rt2, p2)) if p1.length == p2.length =>
        new ScFunctionType(lub(rt1, rt2),
          collection.immutable.Seq(p1.toSeq.zip(p2.toSeq).map({case (t1, t2) => glb(t1, t2)}).toSeq: _*),
          fun.getProject, fun.getScope)
      case (t1@ScTupleType(c1), ScTupleType(c2)) if c1.length == c2.length =>
        new ScTupleType(collection.immutable.Seq(c1.toSeq.zip(c2.toSeq).map({case (t1, t2) => lub(t1, t2)}).toSeq: _*), t1.getProject)

      case (ScSkolemizedType(_, Nil, _, upper), _) => lub(upper, t2)
      case (_, ScSkolemizedType(_, Nil, _, upper)) => lub(t1, upper)
      case (ScPolymorphicType(_, Nil, _, upper), _) => lub(upper.v, t2)
      case (_, ScPolymorphicType(_, Nil, _, upper)) => lub(t1, upper.v)
      case (s: ScSingletonType, _) => lub(s.pathType, t2)
      case (_, s: ScSingletonType) => lub(t1, s.pathType)
      case (ex : ScExistentialType, _) => lub(ex.skolem, t2)
      case (_, ex : ScExistentialType) => lub(t1, ex.skolem)
      case (_: ValType, _: ValType) => types.AnyVal
      case (JavaArrayType(arg1), JavaArrayType(arg2)) => {
        JavaArrayType(calcForTypeParamWithotVariance(arg1, arg2))
      }
      case (JavaArrayType(arg), ScParameterizedType(des, args)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        ScParameterizedType(des, Seq(calcForTypeParamWithotVariance(arg, args(0))))
      }
      case (ScParameterizedType(des, args), JavaArrayType(arg)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        ScParameterizedType(des, Seq(calcForTypeParamWithotVariance(arg, args(0))))
      }
      case _ => (ScType.extractClassType(t1), ScType.extractClassType(t2)) match {
        case (Some((clazz1, subst1)), Some((clazz2, subst2))) => {
          val set = new HashSet[ScType]
          val supers = GenericsUtil.getLeastUpperClasses(clazz1, clazz2) //todo: rewrite it for scala (to have for compound types)
          for (sup <- supers) {
            set += getTypeForAppending(clazz1, subst1, clazz2, subst2, sup, depth)
          }
          set.toArray match {
            case a: Array[ScType] if a.length == 0 => Any
            case a: Array[ScType] if a.length == 1 => a(0)
            case many => new ScCompoundType(collection.immutable.Seq(many.toSeq: _*), Seq.empty, Seq.empty, ScSubstitutor.empty)
          }
        }
        case _ => Any //todo: compound types
      }
    }
  }

  private def calcForTypeParamWithotVariance(substed1: ScType, substed2: ScType): ScType = {
    if (substed1 equiv substed2) substed1 else Any
  }

  private def getTypeForAppending(clazz1: PsiClass, subst1: ScSubstitutor,
                                  clazz2: PsiClass, subst2: ScSubstitutor,
                                  baseClass: PsiClass, depth: Int): ScType = {
    if (baseClass.getTypeParameters.length == 0) return ScDesignatorType(baseClass)
    (superSubstitutor(baseClass, clazz1, subst1), superSubstitutor(baseClass, clazz2, subst2)) match {
      case (Some(superSubst1), Some(superSubst2)) => {
        val tp = ScParameterizedType(ScDesignatorType(baseClass), baseClass.
                getTypeParameters.map(tp => ScalaPsiManager.instance(baseClass.getProject).typeVariable(tp)))
        val tp1 = superSubst1.subst(tp).asInstanceOf[ScParameterizedType]
        val tp2 = superSubst2.subst(tp).asInstanceOf[ScParameterizedType]
        val resTypeArgs = new ArrayBuffer[ScType]
        for (i <- 0 until baseClass.getTypeParameters.length) {
          val substed1 = tp1.typeArgs.apply(i)
          val substed2 = tp2.typeArgs.apply(i)
          resTypeArgs += (baseClass.getTypeParameters.apply(i) match {
            case scp: ScTypeParam if scp.isCovariant => if (depth < 2) lub(substed1, substed2, depth + 1) else Any
            case scp: ScTypeParam if scp.isContravariant => glb(substed1, substed2)
            case _ => calcForTypeParamWithotVariance(substed1, substed2) //todo: _ >: substed1 with substed2
          })
        }
        return ScParameterizedType(ScDesignatorType(baseClass), resTypeArgs.toSeq)
      }
      case _ => Any
    }
  }

  def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor) : Option[ScSubstitutor] = {
    //if (drv.isInheritor(base, true)) Some(ScSubstitutor.empty) else None
    superSubstitutor(base, drv, drvSubst, HashSet[PsiClass]())
  }

  private def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor,
                               visited : Set[PsiClass]) : Option[ScSubstitutor] = {
    //todo: move somewhere and cache
    if (base == drv) Some(drvSubst) else {
      if (visited.contains(drv)) None else {
        visited += drv
        val superTypes: Seq[ScType] = drv match {
          case td: ScTemplateDefinition => td.superTypes
          case _ => drv.getSuperTypes.map{t => ScType.create(t, drv.getProject)}
        }
        val iterator = superTypes.iterator
        while(iterator.hasNext) {
          val st = iterator.next
          ScType.extractClassType(st) match {
            case None =>
            case Some((c, s)) => superSubstitutor(base, c, s, visited) match {
              case None =>
              case Some(s) => return Some(s.followed(drvSubst))
            }
          }
        }
        None
      }
    }
  }

  def putAliases(template : ScTemplateDefinition, s : ScSubstitutor): ScSubstitutor = {
    var run = s
    for (alias <- template.aliases) {
      alias match {
        case aliasDef: ScTypeAliasDefinition if s.aliasesMap.get(aliasDef.name) == None =>
          run = run bindA (aliasDef.name, {() => aliasDef.aliasedType(TypingContext.empty).getOrElse(Any)})
        case _ =>
      }
    }
    run
  }
}