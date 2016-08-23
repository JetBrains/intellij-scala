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

import scala.collection.immutable.HashSet
import scala.collection.mutable

object BaseTypes {
  def get(`type`: ScType)
         (implicit typeSystem: TypeSystem = ScalaTypeSystem): Seq[ScType] =
    getInner(`type`, HashSet.empty, notAll = false)

  private def getInner(`type`: ScType,
                       visitedAliases: HashSet[ScTypeAlias],
                       notAll: Boolean)
                      (implicit typeSystem: TypeSystem): Seq[ScType] = {
    ProgressManager.checkCanceled()
    `type` match {
      case ScDesignatorType(td: ScTemplateDefinition) =>
        reduce(td.superTypes.flatMap(tp => if (!notAll) getInner(tp, visitedAliases, notAll) ++ Seq(tp) else Seq(tp)))
      case ScDesignatorType(c: PsiClass) =>
        reduce(c.getSuperTypes.flatMap { p => {
          val tp = p.toScType()
          (if (!notAll) getInner(tp, visitedAliases, notAll)
          else Seq()) ++ Seq(tp)
        }
        })
      case ScDesignatorType(ta: ScTypeAliasDefinition) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        getInner(ta.aliasedType.getOrElse(return Seq.empty), visitedAliases + ta, notAll = false)
      case ScThisType(clazz) =>
        getInner(clazz.getTypeWithProjections(TypingContext.empty).getOrElse(return Seq.empty), visitedAliases, notAll = false)
      case TypeParameterType(Nil, _, upper, _) =>
        getInner(upper.v, visitedAliases, notAll)
      case ScExistentialArgument(_, Nil, _, upper) =>
        getInner(upper, visitedAliases, notAll)
      case _: JavaArrayType => Seq(Any)
      case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        getInner(p.actualSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases + ta, notAll = false)
      case ParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.typesCallSubstitutor(ta.typeParameters.map(_.nameAndId), args)
        getInner(genericSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases + ta, notAll = false)
      case ParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(_.nameAndId), args)
        val s = p.actualSubst.followed(genericSubst)
        getInner(s.subst(ta.aliasedType.getOrElse(return Seq.empty)), visitedAliases + ta, notAll = false)
      case p: ScParameterizedType =>
        p.designator.extractClass() match {
          case Some(td: ScTypeDefinition) =>
            reduce(td.superTypes.flatMap { tp =>
              if (!notAll) getInner(p.substitutor.subst(tp), visitedAliases, notAll = false) ++ Seq(p.substitutor.subst(tp))
              else Seq(p.substitutor.subst(tp))
            })
          case Some(clazz) =>
            val s = p.substitutor
            reduce(clazz.getSuperTypes.flatMap { t => {
              val substituted = s.subst(t.toScType())
              (if (!notAll) getInner(substituted, visitedAliases, notAll = false)
              else Seq()) ++ Seq(substituted)
            }
            })
          case _ => Seq.empty
        }
      case ex: ScExistentialType => getInner(ex.quantified, visitedAliases, notAll).map {
        _.unpackedType
      }
      case ScCompoundType(comps, _, _) => reduce(if (notAll) comps else comps.flatMap(comp => getInner(comp, visitedAliases, notAll = false) ++ Seq(comp)))
      case proj@ScProjectionType(_, elem, _) =>
        val s = proj.actualSubst
        elem match {
          case td: ScTypeDefinition => reduce(td.superTypes.flatMap { tp =>
            if (!notAll) getInner(s.subst(tp), visitedAliases, notAll = false) ++ Seq(s.subst(tp))
            else Seq(s.subst(tp))
          })
          case c: PsiClass =>
            reduce(c.getSuperTypes.flatMap { st => {
              val substituted = s.subst(st.toScType())
              (if (!notAll) getInner(substituted, visitedAliases, notAll = false)
              else Seq()) ++ Seq(substituted)
            }
            })
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }

  private def reduce(types: Seq[ScType])
                    (implicit typeSystem: TypeSystem): Seq[ScType] = {
    val typeToClass = mutable.Map[ScType, PsiClass]()
    val classToTypes = new mutable.HashMap[PsiClass, mutable.Set[ScType]] with mutable.MultiMap[PsiClass, ScType]

    for (t <- types;
         c <- t.extractClass()) {
      typeToClass(t) = c
      classToTypes.addBinding(c, t)
    }

    typeToClass.filter {
      case (tp, clazz) => classToTypes(clazz).exists(tp.conforms(_))
    }.keys.toSeq
  }
}
