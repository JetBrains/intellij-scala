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
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, TypingContext}

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
      case ScDesignatorType(definition: ScTemplateDefinition) =>
        reduce(definition.superTypes)
      case ScDesignatorType(clazz: PsiClass) =>
        reduce(clazz.getSuperTypes.map(_.toScType()))
      case ScDesignatorType(aliasDefinition: ScTypeAliasDefinition) =>
        visitedAliases.contains(aliasDefinition) match {
          case false => getInner(aliasDefinition.aliasedType(), visitedAliases + aliasDefinition)
          case _ => Seq.empty
        }
      case ScThisType(clazz) =>
        getInner(clazz.getTypeWithProjections(TypingContext.empty), visitedAliases)
      case TypeParameterType(Nil, _, upper, _) =>
        getInner(upper.v)
      case ScExistentialArgument(_, Nil, _, upper) =>
        getInner(upper)
      case _: JavaArrayType =>
        Seq(Any)
      case existentialType: ScExistentialType =>
        getInner(existentialType.quantified).map(_.unpackedType)
      case compoundType: ScCompoundType =>
        reduce(compoundType.components)

      case ParameterizedType(ScDesignatorType(aliasDefinition: ScTypeAliasDefinition), arguments) =>
        visitedAliases.contains(aliasDefinition) match {
          case false =>
            val substitutor = ScalaPsiUtil.typesCallSubstitutor(aliasDefinition.typeParameters.map(_.nameAndId), arguments)

            val substituted = aliasDefinition.aliasedType().map {
              substitutor.subst
            }
            getInner(substituted, visitedAliases + aliasDefinition)
          case _ => Seq.empty
        }
      case ParameterizedType(projectionType: ScProjectionType, arguments) if projectionType.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val aliasDefinition = projectionType.actualElement.asInstanceOf[ScTypeAliasDefinition]

        visitedAliases.contains(aliasDefinition) match {
          case false =>
            val genericSubstitutor = ScalaPsiUtil.typesCallSubstitutor(aliasDefinition.typeParameters.map(_.nameAndId), arguments)
            val substitutor = projectionType.actualSubst.followed(genericSubstitutor)

            val substituted = aliasDefinition.aliasedType().map {
              substitutor.subst
            }
            getInner(substituted, visitedAliases + aliasDefinition)
          case _ => Seq.empty
        }
      case parameterizedType: ScParameterizedType =>
        val types = parameterizedType.designator.extractClass() match {
          case Some(td: ScTypeDefinition) => td.superTypes
          case Some(clazz) => clazz.getSuperTypes.toSeq.map(_.toScType())
          case _ => Seq.empty
        }

        val substitutor = parameterizedType.substitutor
        reduce(types.map {
          substitutor.subst
        })

      case projectionType: ScProjectionType if projectionType.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val aliasDefinition = projectionType.actualElement.asInstanceOf[ScTypeAliasDefinition]

        visitedAliases.contains(aliasDefinition) match {
          case false =>
            val substituted = aliasDefinition.aliasedType().map {
              projectionType.actualSubst.subst
            }
            getInner(substituted, visitedAliases + aliasDefinition)
          case _ => Seq.empty
        }
      case projectionType: ScProjectionType =>
        val types = projectionType.element match {
          case td: ScTypeDefinition => td.superTypes
          case c: PsiClass => c.getSuperTypes.toSeq.map(_.toScType())
          case _ => Seq.empty
        }

        val substitutor = projectionType.actualSubst
        reduce(types.map {
          substitutor.subst
        })
      case _ => Seq.empty
    }
  }

  private def getInner(typeResult: TypeResult[ScType],
                       visitedAliases: HashSet[ScTypeAlias])
                      (implicit typeSystem: TypeSystem,
                       notAll: Boolean): Seq[ScType] =
    typeResult.toOption.toSeq.flatMap {
      getInner(_)(typeSystem, visitedAliases, notAll)
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
