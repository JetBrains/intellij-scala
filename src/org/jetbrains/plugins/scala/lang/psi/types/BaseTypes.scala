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
    getInner(`type`)(typeSystem, HashSet.empty, notAll = false)

  private def getInner(`type`: ScType)
                      (implicit typeSystem: TypeSystem,
                       visitedAliases: HashSet[ScTypeAlias],
                       notAll: Boolean): Seq[ScType] = {
    ProgressManager.checkCanceled()

    `type` match {
      case ScDesignatorType(td: ScTemplateDefinition) =>
        reduce(td.superTypes)
      case ScDesignatorType(c: PsiClass) =>
        reduce(c.getSuperTypes.map(_.toScType()))
      case ScDesignatorType(ta: ScTypeAliasDefinition) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        getInner(ta.aliasedType.getOrElse(return Seq.empty))(typeSystem, visitedAliases + ta, notAll = false)
      case ScThisType(clazz) =>
        getInner(clazz.getTypeWithProjections(TypingContext.empty).getOrElse(return Seq.empty))(typeSystem, visitedAliases, notAll = false)
      case TypeParameterType(Nil, _, upper, _) =>
        getInner(upper.v)
      case ScExistentialArgument(_, Nil, _, upper) =>
        getInner(upper)
      case _: JavaArrayType => Seq(Any)
      case p: ScProjectionType if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        getInner(p.actualSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)))(typeSystem, visitedAliases + ta, notAll = false)
      case ParameterizedType(ScDesignatorType(ta: ScTypeAliasDefinition), args) =>
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.typesCallSubstitutor(ta.typeParameters.map(_.nameAndId), args)
        getInner(genericSubst.subst(ta.aliasedType.getOrElse(return Seq.empty)))(typeSystem, visitedAliases + ta, notAll = false)
      case ParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val ta = p.actualElement.asInstanceOf[ScTypeAliasDefinition]
        if (visitedAliases.contains(ta)) return Seq.empty
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(_.nameAndId), args)
        val s = p.actualSubst.followed(genericSubst)
        getInner(s.subst(ta.aliasedType.getOrElse(return Seq.empty)))(typeSystem, visitedAliases + ta, notAll = false)
      case p: ScParameterizedType =>
        val types = p.designator.extractClass() match {
          case Some(td: ScTypeDefinition) => td.superTypes
          case Some(clazz) => clazz.getSuperTypes.toSeq.map(_.toScType())
          case _ => Seq.empty
        }

        val substitutor = p.substitutor
        reduce(types.map {
          substitutor.subst
        })
      case ex: ScExistentialType =>
        getInner(ex.quantified).map(_.unpackedType)
      case ScCompoundType(comps, _, _) =>
        reduce(comps)
      case proj@ScProjectionType(_, elem, _) =>
        val types = elem match {
          case td: ScTypeDefinition => td.superTypes
          case c: PsiClass => c.getSuperTypes.toSeq.map(_.toScType())
          case _ => Seq.empty
        }

        val substitutor = proj.actualSubst
        reduce(types.map {
          substitutor.subst
        })
      case _ => Seq.empty
    }
  }

  private def reduce(types: Seq[ScType])
                    (implicit typeSystem: TypeSystem,
                     visitedAliases: HashSet[ScTypeAlias],
                     notAll: Boolean): Seq[ScType] = {
    val updatedTypes = types.flatMap { tp =>
      (if (!notAll) getInner(tp) else Seq.empty) :+ tp
    }

    val typeToClass = mutable.Map[ScType, PsiClass]()
    val classToTypes = new mutable.HashMap[PsiClass, mutable.Set[ScType]] with mutable.MultiMap[PsiClass, ScType]

    for (t <- updatedTypes;
         c <- t.extractClass()) {
      typeToClass(t) = c
      classToTypes.addBinding(c, t)
    }

    typeToClass.filter {
      case (tp, clazz) => classToTypes(clazz).exists(tp.conforms(_))
    }.keys.toSeq
  }
}
