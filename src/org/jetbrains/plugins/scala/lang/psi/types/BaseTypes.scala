package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.expr.ScThisReference
import com.intellij.psi.{PsiTypeParameter, PsiClass}
import api.statements.params.ScTypeParam
import resolve.ScalaResolveResult
import _root_.scala.collection.mutable.{Set, HashMap, MultiMap}
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

object BaseTypes {
  def get(t : ScType, notAll: Boolean = false) : Seq[ScType] = t match {
    case classT@ScDesignatorType(td : ScTemplateDefinition) => reduce(td.superTypes.flatMap(tp => if (!notAll) BaseTypes.get(tp, notAll) ++ Seq(tp) else Seq(tp)))
    case classT@ScDesignatorType(c : PsiClass) =>
      reduce(c.getSuperTypes.flatMap{p => if (!notAll) BaseTypes.get(ScType.create(p, c.getProject), notAll) ++
              Seq(ScType.create(p, c.getProject)) else Seq(ScType.create(p, c.getProject))})
    case ScPolymorphicType(_, Nil, _, upper) => get(upper.v, notAll)
    case ScSkolemizedType(_, Nil, _, upper) => get(upper, notAll)
    case p : ScParameterizedType => {
      ScType.extractClassType(p.designator) match {
        case Some((td: ScTypeDefinition, _)) =>
          reduce(td.superTypes.flatMap {tp => if (!notAll) BaseTypes.get(p.substitutor.subst(tp), notAll) ++ Seq(p.substitutor.subst(tp)) else Seq(p.substitutor.subst(tp))})
        case Some((clazz: PsiClass, _)) => {
          val s = p.substitutor
          reduce(clazz.getSuperTypes.flatMap {t => if (!notAll) BaseTypes.get(s.subst(ScType.create(t, clazz.getProject)), notAll) ++
                  Seq(s.subst(ScType.create(t, clazz.getProject))) else Seq(s.subst(ScType.create(t, clazz.getProject)))})
        }
        case _ => Seq.empty
      }
    }
    case sin : ScSingletonType => get(sin.pathType, notAll)
    case ScExistentialType(q, wilds) => get(q, notAll).map{bt => ScExistentialTypeReducer.reduce(bt, wilds)}
    case ScCompoundType(comps, _, _) => reduce(comps)
    case proj@ScProjectionType(p, _) => proj.resolveResult match {
      case Some(ScalaResolveResult(td : ScTypeDefinition, s)) => td.superTypes.map{s.subst _}
      case Some(ScalaResolveResult(c : PsiClass, s)) =>
        c.getSuperTypes.map{st => s.subst(ScType.create(st, c.getProject))}
      case _ => Seq.empty
    }
    case t: ScTupleType => t.resolveTupleTrait match {
      case Some(t: ScType) => get(t, notAll)
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }

  def reduce (types : Seq[ScType]) : Seq[ScType] = {
    val res = new HashMap[PsiClass, ScType]
    object all extends HashMap[PsiClass, Set[ScType]] with MultiMap[PsiClass, ScType]
    for (t <- types) {
      ScType.extractClassType(t) match {
        case Some((c, _)) => {
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