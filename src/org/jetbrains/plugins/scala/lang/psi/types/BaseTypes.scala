package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiClass, PsiNamedElement}
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
        getInner(aliasDefinition) {
          identity
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
      case parameterizedType: ScParameterizedType =>
        getInner(parameterizedType)
      case projectionType: ScProjectionType =>
        getInner(projectionType)
      case _ => Seq.empty
    }
  }

  private def getInner(parameterizedType: ParameterizedType)
                      (implicit typeSystem: TypeSystem,
                       visitedAliases: HashSet[ScTypeAlias],
                       notAll: Boolean): Seq[ScType] = {
    def substitutor(aliasDefinition: ScTypeAliasDefinition) =
      ScalaPsiUtil.typesCallSubstitutor(aliasDefinition.typeParameters.map(_.nameAndId), parameterizedType.typeArguments)

    parameterizedType.designator match {
      case ScDesignatorType(aliasDefinition: ScTypeAliasDefinition) =>
        getInner(aliasDefinition) {
          substitutor(aliasDefinition).subst
        }
      case projectionType: ScProjectionType if projectionType.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val aliasDefinition = projectionType.actualElement.asInstanceOf[ScTypeAliasDefinition]
        getInner(aliasDefinition) {
          projectionType.actualSubst.followed(substitutor(aliasDefinition)).subst
        }
      case designator => reduce(designator.extractClass(), parameterizedType.substitutor)
    }
  }

  private def getInner(projectionType: ScProjectionType)
                      (implicit typeSystem: TypeSystem,
                       visitedAliases: HashSet[ScTypeAlias],
                       notAll: Boolean): Seq[ScType] = projectionType.actualElement match {
    case aliasDefinition: ScTypeAliasDefinition =>
      getInner(aliasDefinition) {
        projectionType.actualSubst.subst
      }
    case _ => reduce(Option(projectionType.element), projectionType.actualSubst)
  }

  private def getInner(typeResult: TypeResult[ScType],
                       visitedAliases: HashSet[ScTypeAlias])
                      (implicit typeSystem: TypeSystem,
                       notAll: Boolean): Seq[ScType] =
    typeResult.toOption.toSeq.flatMap {
      getInner(_)(typeSystem, visitedAliases, notAll)
    }

  private def getInner(aliasDefinition: ScTypeAliasDefinition)
                      (substitute: => (ScType => ScType))
                      (implicit typeSystem: TypeSystem,
                       visitedAliases: HashSet[ScTypeAlias],
                       notAll: Boolean): Seq[ScType] = {
    visitedAliases.contains(aliasDefinition) match {
      case false =>
        val substituted = aliasDefinition.aliasedType().map(substitute)
        getInner(substituted, visitedAliases + aliasDefinition)
      case _ => Seq.empty
    }
  }

  private def reduce(maybeElement: Option[PsiNamedElement],
                     substitutor: ScSubstitutor)
                    (implicit typeSystem: TypeSystem,
                     visitedAliases: HashSet[ScTypeAlias],
                     notAll: Boolean): Seq[ScType] = {
    val types = maybeElement.toSeq.flatMap {
      case definition: ScTypeDefinition => definition.superTypes
      case clazz: PsiClass => clazz.getSuperTypes.toSeq.map(_.toScType())
      case _ => Seq.empty
    }
    reduce(types.map(substitutor.subst))
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
