package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.PsiTypeParameterExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import scala.collection.mutable

object BaseTypes {
  def get(t: ScType, notAll: Boolean = false, visitedAliases: Set[ScTypeAlias] = Set.empty): Seq[ScType] = {
    implicit val project = t.projectContext

    ProgressManager.checkCanceled()
    t match {
      case ScDesignatorType(td : ScTemplateDefinition) =>
        reduce(td.superTypes.flatMap(tp => if (!notAll) BaseTypes.get(tp, notAll, visitedAliases = visitedAliases) ++ Seq(tp) else Seq(tp)))
      case ScDesignatorType(c : PsiClass) =>
        reduce(c.getSuperTypes.flatMap { p => {
          val tp = p.toScType()
          (if (!notAll) BaseTypes.get(tp, notAll, visitedAliases = visitedAliases)
          else Seq()) ++ Seq(tp)
        }
        })
      case ScDesignatorType(ta: ScTypeAliasDefinition) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        BaseTypes.get(ta.aliasedType.getOrElse(return Seq.empty), visitedAliases = visitedAliases + ta)
      case ScThisType(clazz) => BaseTypes.get(clazz.getTypeWithProjections(TypingContext.empty).getOrElse(return Seq.empty), visitedAliases = visitedAliases)
      case TypeParameterType(Nil, _, upper, _) => get(upper.v, notAll, visitedAliases = visitedAliases)
      case ScExistentialArgument(_, Nil, _, upper) => get(upper, notAll, visitedAliases = visitedAliases)
      case _: JavaArrayType => Seq(Any)
      case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        BaseTypes.get(p.actualSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases = visitedAliases + ta)
      case ParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.typesCallSubstitutor(ta.typeParameters.map(_.nameAndId), args)
        BaseTypes.get(genericSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases = visitedAliases + ta)
      case ParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(_.nameAndId), args)
        val s = p.actualSubst.followed(genericSubst)
        BaseTypes.get(s.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases = visitedAliases + ta)
      case p : ScParameterizedType =>
        p.designator.extractClass match {
          case Some(td: ScTypeDefinition) =>
            reduce(td.superTypes.flatMap { tp =>
              if (!notAll) BaseTypes.get(p.substitutor.subst(tp), notAll, visitedAliases = visitedAliases) ++ Seq(p.substitutor.subst(tp)) else Seq(p
                    .substitutor.subst(tp))
            })
          case Some(clazz) =>
            val s = p.substitutor
            reduce(clazz.getSuperTypes.flatMap { t => {
              val substituted = s.subst(t.toScType())
              (if (!notAll) BaseTypes.get(substituted, notAll, visitedAliases = visitedAliases)
              else Seq()) ++ Seq(substituted)
            }
            })
          case _ => Seq.empty
        }
      case ex: ScExistentialType => get(ex.quantified, notAll, visitedAliases = visitedAliases).map { _.unpackedType }
      case ScCompoundType(comps, _, _) => reduce(if (notAll) comps else comps.flatMap(comp => BaseTypes.get(comp, visitedAliases = visitedAliases) ++ Seq(comp)))
      case proj@ScProjectionType(_, elem, _) =>
        val s = proj.actualSubst
        elem match {
          case td : ScTypeDefinition => reduce(td.superTypes.flatMap{tp =>
            if (!notAll) BaseTypes.get(s.subst(tp), visitedAliases = visitedAliases) ++ Seq(s.subst(tp))
            else Seq(s.subst(tp))
          })
          case c : PsiClass =>
            reduce(c.getSuperTypes.flatMap {st =>
            {
              val substituted = s.subst(st.toScType())
              (if (!notAll) BaseTypes.get(substituted, visitedAliases = visitedAliases)
              else Seq()) ++ Seq(substituted)
            }
            })
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }

  def reduce(types: Seq[ScType]): Seq[ScType] = {
    val res = new mutable.HashMap[PsiClass, ScType]
    object all extends mutable.HashMap[PsiClass, mutable.Set[ScType]] with mutable.MultiMap[PsiClass, ScType]
    val iterator = types.iterator
    while (iterator.hasNext) {
       val t = iterator.next()
      t.extractClass match {
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
