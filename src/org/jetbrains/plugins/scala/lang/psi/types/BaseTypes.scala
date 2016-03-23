package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import scala.collection.immutable.HashSet
import scala.collection.mutable

object BaseTypes {
  def get(t: ScType, notAll: Boolean = false, visitedAliases: HashSet[ScTypeAlias] = HashSet.empty)
         (implicit typeSystem: TypeSystem = ScalaTypeSystem): Seq[ScType] = {
    ProgressManager.checkCanceled()
    t match {
      case ScDesignatorType(td : ScTemplateDefinition) =>
        reduce(td.superTypes.flatMap(tp => if (!notAll) BaseTypes.get(tp, notAll, visitedAliases = visitedAliases) ++ Seq(tp) else Seq(tp)))
      case ScDesignatorType(c : PsiClass) =>
        reduce(c.getSuperTypes.flatMap{p => if (!notAll) BaseTypes.get(ScType.create(p, c.getProject), notAll, visitedAliases = visitedAliases) ++
                Seq(ScType.create(p, c.getProject)) else Seq(ScType.create(p, c.getProject))})
      case ScDesignatorType(ta: ScTypeAliasDefinition) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        BaseTypes.get(ta.aliasedType.getOrElse(return Seq.empty), visitedAliases = visitedAliases + ta)
      case ScThisType(clazz) => BaseTypes.get(clazz.getTypeWithProjections(TypingContext.empty).getOrElse(return Seq.empty), visitedAliases = visitedAliases)
      case ScTypeParameterType(_, Nil, _, upper, _) => get(upper.v, notAll, visitedAliases = visitedAliases)
      case ScSkolemizedType(_, Nil, _, upper) => get(upper, notAll, visitedAliases = visitedAliases)
      case a: JavaArrayType => Seq(types.Any)
      case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        BaseTypes.get(p.actualSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases = visitedAliases + ta)
      case ScParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))), args)
        BaseTypes.get(genericSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases = visitedAliases + ta)
      case ScParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp)
                )), args)
        val s = p.actualSubst.followed(genericSubst)
        BaseTypes.get(s.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases = visitedAliases + ta)
      case p : ScParameterizedType =>
        ScType.extractClass(p.designator) match {
          case Some(td: ScTypeDefinition) =>
            reduce(td.superTypes.flatMap { tp =>
              if (!notAll) BaseTypes.get(p.substitutor.subst(tp), notAll, visitedAliases = visitedAliases) ++ Seq(p.substitutor.subst(tp)) else Seq(p
                    .substitutor.subst(tp))
            })
          case Some(clazz) =>
            val s = p.substitutor
            reduce(clazz.getSuperTypes.flatMap {t => if (!notAll) BaseTypes.get(s.subst(ScType.create(t, clazz.getProject)), notAll, visitedAliases = visitedAliases) ++
                    Seq(s.subst(ScType.create(t, clazz.getProject))) else Seq(s.subst(ScType.create(t, clazz.getProject)))})
          case _ => Seq.empty
        }
      case ScExistentialType(q, wilds) => get(q, notAll, visitedAliases = visitedAliases).map{bt => ScExistentialType(bt, wilds).simplify()}
      case ScCompoundType(comps, _, _) => reduce(if (notAll) comps else comps.flatMap(comp => BaseTypes.get(comp, visitedAliases = visitedAliases) ++ Seq(comp)))
      case proj@ScProjectionType(p, elem, _) =>
        val s = proj.actualSubst
        elem match {
          case td : ScTypeDefinition => reduce(td.superTypes.flatMap{tp =>
            if (!notAll) BaseTypes.get(s.subst(tp), visitedAliases = visitedAliases) ++ Seq(s.subst(tp))
            else Seq(s.subst(tp))
          })
          case c : PsiClass =>
            reduce(c.getSuperTypes.flatMap {st =>
            {
              val proj = c.getProject
              if (!notAll) BaseTypes.get(s.subst(ScType.create(st, proj)), visitedAliases = visitedAliases) ++ Seq(s.subst(ScType.create(st, proj)))
              else Seq(s.subst(ScType.create(st, proj)))
            }
            })
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }

  def reduce(types: Seq[ScType])
            (implicit typeSystem: TypeSystem): Seq[ScType] = {
    val res = new mutable.HashMap[PsiClass, ScType]
    object all extends mutable.HashMap[PsiClass, mutable.Set[ScType]] with mutable.MultiMap[PsiClass, ScType]
    val iterator = types.iterator
    while (iterator.hasNext) {
       val t = iterator.next()
      ScType.extractClass(t) match {
        case Some(c) =>
          val isBest = all.get(c) match {
            case None => true
            case Some(ts) => !ts.exists(t.conforms(_))
          }
          if (isBest) res += ((c, t))
          all.addBinding(c, t)
        case None => //not a class type
      }
    }
    res.values.toList
  }
}