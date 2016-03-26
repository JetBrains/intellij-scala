package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation.shouldExpand
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
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
      conforms(`type`, new ScUndefinedSubstitutor(), checkWeak = false)._1
    }

    def weakConforms(`type`: ScType)
                    (implicit typeSystem: TypeSystem) = {
      conforms(`type`, new ScUndefinedSubstitutor(), checkWeak = true)._1
    }

    def conforms(`type`: ScType,
                 undefinedSubstitutor: ScUndefinedSubstitutor,
                 checkWeak: Boolean = false)
                (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
      typeSystem.conformance.conformsInner(`type`, scType, substitutor = undefinedSubstitutor, checkWeak = checkWeak)
    }

    def glb(`type`: ScType, checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.glb(scType, `type`, checkWeak)
    }

    def lub(`type`: ScType, checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.lub(scType, `type`, checkWeak)
    }

    def removeUndefines() = scType.recursiveUpdate {
      case u: ScUndefinedType => (true, Any)
      case tp: ScType => (false, tp)
    }

    def toPsiType(project: Project,
                  scope: GlobalSearchScope,
                  noPrimitives: Boolean = false,
                  skolemToWildcard: Boolean = false) = {
      project.typeSystem.bridge.toPsiType(scType, project, scope, noPrimitives, skolemToWildcard)
    }

    def extractClass(project: Project = null)
                    (implicit typeSystem: TypeSystem) = {
      typeSystem.bridge.extractClass(scType, project)
    }

    def extractClassType(project: Project = null,
                         visitedAlias: HashSet[ScTypeAlias] = HashSet.empty)
                        (implicit typeSystem: TypeSystem) = {
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
  }

  implicit class ScTypesExt(val types: Seq[ScType]) extends AnyVal {
    def glb(checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.glb(types, checkWeak)
    }

    def lub(checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.glb(types, checkWeak)
    }
  }

}
