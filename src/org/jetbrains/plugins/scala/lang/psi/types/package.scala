package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiType}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation.shouldExpand
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, _}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec
import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */
package object types {

  implicit class ScTypeExt(val scType: ScType) extends AnyVal {
    def equiv(`type`: ScType)(implicit typeSystem: TypeSystem): Boolean = {
      typeSystem.equivalence.equiv(scType, `type`)
    }

    def equiv(`type`: ScType, undefinedSubstitutor: ScUndefinedSubstitutor, falseUndef: Boolean = true)
             (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
      typeSystem.equivalence.equivInner(scType, `type`, undefinedSubstitutor, falseUndef)
    }

    def conforms(`type`: ScType)
                (implicit typeSystem: TypeSystem): Boolean = {
      conforms(`type`, ScUndefinedSubstitutor(), checkWeak = false)._1
    }

    def weakConforms(`type`: ScType)
                    (implicit typeSystem: TypeSystem): Boolean = {
      conforms(`type`, ScUndefinedSubstitutor(), checkWeak = true)._1
    }

    def conforms(`type`: ScType,
                 undefinedSubstitutor: ScUndefinedSubstitutor,
                 checkWeak: Boolean = false)
                (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
      typeSystem.conformance.conformsInner(`type`, scType, substitutor = undefinedSubstitutor, checkWeak = checkWeak)
    }

    def glb(`type`: ScType, checkWeak: Boolean = false)(implicit typeSystem: TypeSystem): ScType = {
      typeSystem.bounds.glb(scType, `type`, checkWeak)
    }

    def lub(`type`: ScType, checkWeak: Boolean = true)(implicit typeSystem: TypeSystem): ScType = {
      typeSystem.bounds.lub(scType, `type`, checkWeak)
    }

    def removeUndefines(): ScType = scType.recursiveUpdate {
      case _: UndefinedType => (true, Any)
      case tp: ScType => (false, tp)
    }

    def toPsiType(project: Project,
                  scope: GlobalSearchScope,
                  noPrimitives: Boolean = false,
                  skolemToWildcard: Boolean = false): PsiType = {
      project.typeSystem.bridge.toPsiType(scType, project, scope, noPrimitives, skolemToWildcard)
    }

    def extractClass(project: Project = null)
                    (implicit typeSystem: TypeSystem): Option[PsiClass] = {
      typeSystem.bridge.extractClass(scType, project)
    }

    def extractClassType(project: Project = null,
                         visitedAlias: HashSet[ScTypeAlias] = HashSet.empty)
                        (implicit typeSystem: TypeSystem): Option[(PsiClass, ScSubstitutor)] = {
      typeSystem.bridge.extractClassType(scType, project, visitedAlias)
    }

    @tailrec
    final def removeAliasDefinitions(visited: HashSet[ScType] = HashSet.empty, expandableOnly: Boolean = false): ScType = {
      if (visited.contains(scType)) {
        return scType
      }

      var updated = false
      val result = scType.recursiveUpdate {
        `type` => `type`.isAliasType match {
          case Some(AliasType(ta: ScTypeAliasDefinition, _, upper)) if !expandableOnly || shouldExpand(ta) =>
            updated = true
            (true, upper.getOrAny)
          case _ => (false, `type`)
        }
      }
      if (updated) result.removeAliasDefinitions(visited + scType, expandableOnly) else scType
    }

    def extractDesignatorSingleton: Option[ScType] = scType match {
      case desinatorOwner: DesignatorOwner => desinatorOwner.designatorSingletonType
      case _ => None
    }

    def tryExtractDesignatorSingleton: ScType = extractDesignatorSingleton.getOrElse(scType)

    /**
      * Returns named element associated with type.
      * If withoutAliases is true expands alias definitions first
      *
      * @param withoutAliases need to expand alias or not
      * @return element and substitutor
      */
    def extractDesignated(implicit withoutAliases: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = scType match {
      case nonValueType: NonValueType =>
        nonValueType.inferValueType.extractDesignated
      case designatorOwner: DesignatorOwner =>
        designatorOwner.designated
      case parameterizedType: ParameterizedType =>
        parameterizedType.designator.extractDesignated.map {
          case (element, substitutor) => (element, substitutor.followed(parameterizedType.substitutor))
        }
      case stdType: StdType =>
        stdType.asClass(DecompilerUtil.obtainProject).map {
          (_, ScSubstitutor.empty)
        }
      case TypeParameterType(_, _, _, psiTypeParameter) =>
        Some(psiTypeParameter, ScSubstitutor.empty)
      case _ => None
    }
  }

  implicit class ScTypesExt(val types: Seq[ScType]) extends AnyVal {
    def glb(checkWeak: Boolean = false)(implicit typeSystem: TypeSystem): ScType = {
      typeSystem.bounds.glb(types, checkWeak)
    }

    def lub(checkWeak: Boolean = true)(implicit typeSystem: TypeSystem): ScType = {
      typeSystem.bounds.glb(types, checkWeak)
    }
  }

}
