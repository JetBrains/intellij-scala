package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi.{PsiClass}
import resolve.ScalaResolveResult
import _root_.scala.collection.mutable.{Set, HashMap, MultiMap}
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import com.intellij.openapi.progress.ProgressManager

object BaseTypes {
  def get(t : ScType, notAll: Boolean = false) : Seq[ScType] = {
    ProgressManager.checkCanceled
    t match {
      case classT@ScDesignatorType(td : ScTemplateDefinition) => reduce(td.superTypes.flatMap(tp => if (!notAll) BaseTypes.get(tp, notAll) ++ Seq(tp) else Seq(tp)))
      case classT@ScDesignatorType(c : PsiClass) =>
        reduce(c.getSuperTypes.flatMap{p => if (!notAll) BaseTypes.get(ScType.create(p, c.getProject), notAll) ++
                Seq(ScType.create(p, c.getProject)) else Seq(ScType.create(p, c.getProject))})
      case ScPolymorphicType(_, Nil, _, upper) => get(upper.v, notAll)
      case ScSkolemizedType(_, Nil, _, upper) => get(upper, notAll)
      case p : ScParameterizedType => {
        ScType.extractClass(p.designator) match {
          case Some(td: ScTypeDefinition) =>
            reduce(td.superTypes.flatMap {tp => if (!notAll) BaseTypes.get(p.substitutor.subst(tp), notAll) ++ Seq(p.substitutor.subst(tp)) else Seq(p.substitutor.subst(tp))})
          case Some(clazz) => {
            val s = p.substitutor
            reduce(clazz.getSuperTypes.flatMap {t => if (!notAll) BaseTypes.get(s.subst(ScType.create(t, clazz.getProject)), notAll) ++
                    Seq(s.subst(ScType.create(t, clazz.getProject))) else Seq(s.subst(ScType.create(t, clazz.getProject)))})
          }
          case _ => Seq.empty
        }
      }
      case sin : ScSingletonType => get(sin.pathType, notAll)
      case ScExistentialType(q, wilds) => get(q, notAll).map{bt => ScExistentialTypeReducer.reduce(bt, wilds)}
      case ScCompoundType(comps, _, _, _) => reduce(if (notAll) comps else comps.flatMap(comp => BaseTypes.get(comp) ++ Seq(comp)))
      case proj@ScProjectionType(p, _) => proj.resolveResult match {
        case Some(ScalaResolveResult(td : ScTypeDefinition, s)) => reduce(td.superTypes.flatMap{tp =>
          if (!notAll) BaseTypes.get(s.subst(tp)) ++ Seq(s.subst(tp))
          else Seq(s.subst(tp))
        })
        case Some(ScalaResolveResult(c : PsiClass, s)) =>
          reduce(c.getSuperTypes.flatMap {st =>
            {
              val proj = c.getProject
              if (!notAll) BaseTypes.get(s.subst(ScType.create(st, proj))) ++ Seq(s.subst(ScType.create(st, proj)))
              else Seq(s.subst(ScType.create(st, proj)))
            }
          })
        case _ => Seq.empty
      }
      case t: ScTupleType => t.resolveTupleTrait match {
        case Some(t: ScType) => get(t, notAll)
        case _ => Seq.empty
      }
      case f: ScFunctionType => f.resolveFunctionTrait match {
        case Some(t: ScType) => get(t, notAll)
        case _ => Seq.empty
      }
      case _ => Seq.empty
    }
  }

  def reduce(types : Seq[ScType]) : Seq[ScType] = {
    val res = new HashMap[PsiClass, ScType]
    object all extends HashMap[PsiClass, Set[ScType]] with MultiMap[PsiClass, ScType]
    val iterator = types.iterator
    while (iterator.hasNext) {
       val t = iterator.next
      ScType.extractClass(t) match {
        case Some(c) => {
          val isBest = all.get(c) match {
            case None => true
            case Some(ts) => ts.find(t1 => !Conformance.conforms(t1, t)) == None
          }
          if (isBest) res += ((c, t))
          all.addBinding(c, t)
        }
        case None => //not a class type
      }
    }
    res.values.toList
  }
}