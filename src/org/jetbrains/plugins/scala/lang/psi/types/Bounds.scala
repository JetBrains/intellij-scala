package org.jetbrains.plugins.scala.lang.psi.types

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import _root_.scala.collection.mutable.{Set, HashSet}
import api.statements.params.ScTypeParam
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.PsiClass

object Bounds {

  def glb(t1: ScType, t2: ScType) = {
    if (t1.conforms(t2)) t1
    else if (t2.conforms(t1)) t2
    else new ScCompoundType(Array(t1, t2), Seq.empty, Seq.empty)
  }

  def lub(t1: ScType, t2: ScType): ScType = lub(t1, t2, 0)

  private def lub(t1: ScType, t2: ScType, depth : Int): ScType = {
    if (t1.conforms(t2)) t2
    else if (t2.conforms(t1)) t1
    else (t1, t2) match {
      case (ScFunctionType(rt1, p1), ScFunctionType(rt2, p2)) if p1.length == p2.length =>
        ScFunctionType(lub(rt1, rt2), p1.toArray.zip(p2.toArray).map{case (t1, t2) => glb(t1, t2)})
      case (ScTupleType(c1), ScTupleType(c2)) if c1.length == c2.length =>
        ScTupleType(c1.toArray.zip(c2.toArray).map{case (t1, t2) => lub(t1, t2)})

      case (ScSkolemizedType(_, Nil, _, upper), _) => lub(upper, t2)
      case (_, ScSkolemizedType(_, Nil, _, upper)) => lub(t1, upper)
      case (ScPolymorphicType(_, Nil, _, upper), _) => lub(upper.v, t2)
      case (_, ScPolymorphicType(_, Nil, _, upper)) => lub(t1, upper.v)
      case (s: ScSingletonType, _) => lub(s.pathType, t2)
      case (_, s: ScSingletonType) => lub(t1, s.pathType)
      case (ex : ScExistentialType, _) => lub(ex.skolem, t2)
      case (_, ex : ScExistentialType) => lub(t1, ex.skolem)
      case (_: ValType, _: ValType) => types.AnyVal

      case _ => ScType.extractClassType(t1) match {
        case Some((c1, s1)) => {
          val set = new HashSet[ScType]
          appendBaseTypes(t2, c1, s1, set, depth)
          set.toArray match {
            case Array() => Any
            case Array(only) => only
            case many => new ScCompoundType(many, Seq.empty, Seq.empty)
          }
        }
        case None => Any //todo compound types
      }
    }
  }

  private def appendBaseTypes(t1 : ScType, clazz2 : PsiClass, s2 : ScSubstitutor, set : Set[ScType], depth : Int) {
    for (base <- BaseTypes.get(t1)) {
      ScType.extractClassType(base) match {
        case Some((cbase, sbase)) => {
          superSubstitutor(cbase, clazz2, s2, new HashSet[PsiClass]) match {
            case Some(superSubst) => {
              val typeParams = cbase.getTypeParameters
              if (typeParams.length > 0) {
                val substRes = typeParams.toList.foldLeft(ScSubstitutor.empty) {
                  (curr, tp) => {
                    val tv = ScalaPsiManager.typeVariable(tp)
                    val substed1 = superSubst.subst(tv)
                    val substed2 = sbase.subst(tv)
                    val t = tp match {
                      case scp: ScTypeParam if scp.isCovariant => if (depth < 2) lub(substed1, substed2, depth + 1) else Any
                      case scp: ScTypeParam if scp.isContravariant => glb(substed1, substed2)
                      case _ => if (substed1 equiv substed2) substed1 else {
                        appendBaseTypes(base, clazz2, s2, set, 0)
                        return
                      }
                    }

                    curr bindT (tv.name, t)
                  }
                }
                set += ScParameterizedType.create(cbase, substRes)
              } else {
                set += new ScDesignatorType(cbase)
              }
            }
            case None => appendBaseTypes(base, clazz2, s2, set, 0)
          }
        }
        case None =>
      }
    }
  }

  def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor) : Option[ScSubstitutor] =
    superSubstitutor(base, drv, drvSubst, HashSet[PsiClass]())

  private def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor, visited : Set[PsiClass]) : Option[ScSubstitutor] =
    //todo: move somewhere and cache
    if (base == drv) Some(drvSubst) else {
      if (visited.contains(drv)) None else {
        visited += drv
        val superTypes = drv match {
          case td: ScTypeDefinition => td.superTypes
          case _ => drv.getSuperTypes.map{t => ScType.create(t, drv.getProject)}
        }
        for (st <- superTypes) {
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