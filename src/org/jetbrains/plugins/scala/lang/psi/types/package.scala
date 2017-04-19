package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiType, PsiTypeParameter}
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation.shouldExpand
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, _}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.util.control.NoStackTrace
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
      typeSystem.conformance.conformsInner(`type`, scType)._1
    }

    def weakConforms(`type`: ScType)
                    (implicit typeSystem: TypeSystem): Boolean = {
      typeSystem.conformance.conformsInner(`type`, scType, checkWeak = true)._1
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

    def toPsiType(noPrimitives: Boolean = false)
                 (implicit elementScope: ElementScope): PsiType = {
      elementScope.typeSystem.bridge.toPsiType(scType, noPrimitives = noPrimitives)
    }

    /**
      * Returns named element associated with type.
      * If withoutAliases is true expands alias definitions first
      *
      * @param expandAliases need to expand alias or not
      * @return element and substitutor
      */
    def extractDesignatedType(expandAliases: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = {
      new DesignatorExtractor(expandAliases, needSubstitutor = true)
        .extractFrom(scType)
    }

    def extractDesignated(expandAliases: Boolean): Option[PsiNamedElement] = {
      new DesignatorExtractor(expandAliases, needSubstitutor = false)
        .extractFrom(scType).map(_._1)
    }

    def extractClassType(project: Project = null,
                         visitedAlias: Set[ScTypeAlias] = Set.empty): Option[(PsiClass, ScSubstitutor)] = {
      new ClassTypeExtractor(project, needSubstitutor = true)
        .extractFrom(scType)
    }

    def extractClass(project: Project = null): Option[PsiClass] = {
      new ClassTypeExtractor(project, needSubstitutor = false)
        .extractFrom(scType).map(_._1)
    }

    def removeAliasDefinitions(expandableOnly: Boolean = false): ScType = {
      def needExpand(ta: ScTypeAliasDefinition) = !expandableOnly || shouldExpand(ta)

      def innerUpdate(tp: ScType, visited: Set[ScType]): ScType = {
        tp.recursiveUpdate {
          `type` => `type`.isAliasType match {
            case Some(AliasType(ta: ScTypeAliasDefinition, _, Failure(_, _))) if needExpand(ta) =>
              (true, Any)
            case Some(AliasType(ta: ScTypeAliasDefinition, _, Success(upper, _))) if needExpand(ta) =>
              if (visited.contains(`type`)) throw RecursionException
              val updated =
                try innerUpdate(upper, visited + `type`)
                catch {
                  case RecursionException =>
                    if (visited.nonEmpty) throw RecursionException
                    else `type`
                }
              (true, updated)
            case _ => (false, `type`)
          }
        }
      }

      innerUpdate(scType, Set.empty)
    }

    def extractDesignatorSingleton: Option[ScType] = scType match {
      case desinatorOwner: DesignatorOwner => desinatorOwner.designatorSingletonType
      case _ => None
    }

    def tryExtractDesignatorSingleton: ScType = extractDesignatorSingleton.getOrElse(scType)
  }

  implicit class ScTypesExt(val types: Seq[ScType]) extends AnyVal {
    def glb(checkWeak: Boolean = false)(implicit typeSystem: TypeSystem): ScType = {
      typeSystem.bounds.glb(types, checkWeak)
    }

    def lub(checkWeak: Boolean = true)(implicit typeSystem: TypeSystem): ScType = {
      typeSystem.bounds.glb(types, checkWeak)
    }
  }

  private trait Extractor[T <: PsiNamedElement] {
    def filter(named: PsiNamedElement, subst: ScSubstitutor): Option[(T, ScSubstitutor)]
    def expandAliases: Boolean
    def project: Project
    def needSubstitutor: Boolean

    def extractFrom(scType: ScType,
                    visitedAliases: Set[ScTypeAliasDefinition] = Set.empty): Option[(T, ScSubstitutor)] = {

      def needExpand(definition: ScTypeAliasDefinition) = expandAliases && !visitedAliases(definition)

      scType match {
        case nonValueType: NonValueType =>
          extractFrom(nonValueType.inferValueType, visitedAliases)
        case thisType: ScThisType => filter(thisType.element, ScSubstitutor(thisType))
        case projType: ScProjectionType =>
          val actualSubst = projType.actualSubst
          val actualElement = projType.actualElement
          actualElement match {
            case definition: ScTypeAliasDefinition if needExpand(definition) =>
              definition.aliasedType.toOption match {
                case Some(ParameterizedType(des, _)) if !needSubstitutor =>
                  extractFrom(actualSubst.subst(des), visitedAliases + definition)
                case Some(tp) =>
                  extractFrom(actualSubst.subst(tp), visitedAliases + definition)
                case _ => None
              }
            case _ => filter(actualElement, actualSubst)
          }
        case designatorOwner: DesignatorOwner =>
          designatorOwner.element match {
            case definition: ScTypeAliasDefinition if needExpand(definition) =>
              definition.aliasedType.toOption.flatMap {
                extractFrom(_, visitedAliases + definition)
              }
            case elem => filter(elem, ScSubstitutor.empty)
          }
        case parameterizedType: ParameterizedType =>
          extractFrom(parameterizedType.designator, visitedAliases).map {
            case (element, substitutor) =>
              val withFollower = if (needSubstitutor) substitutor.followed(parameterizedType.substitutor) else ScSubstitutor.empty
              (element, withFollower)
          }
        case stdType: StdType =>
          stdType.asClass(project).flatMap {
            filter(_, ScSubstitutor.empty)
          }
        case ScExistentialType(quantified, _) =>
          extractFrom(quantified, visitedAliases)
        case TypeParameterType(_, _, _, psiTypeParameter) =>
          filter(psiTypeParameter, ScSubstitutor.empty)
        case _ => None
      }
    }
  }

  private class DesignatorExtractor(override val expandAliases: Boolean, override val needSubstitutor: Boolean) extends Extractor[PsiNamedElement] {
    override def filter(named: PsiNamedElement, subst: ScSubstitutor): Option[(PsiNamedElement, ScSubstitutor)] =
      Some(named, subst)

    override def project: Project = DecompilerUtil.obtainProject
  }

  private class ClassTypeExtractor(givenProject: Project, override val needSubstitutor: Boolean) extends Extractor[PsiClass] {
    override def filter(named: PsiNamedElement, subst: ScSubstitutor): Option[(PsiClass, ScSubstitutor)] =
      named match {
        case _: PsiTypeParameter => None
        case c: PsiClass => Some(c, subst)
        case _ => None
      }

    override def project: Project = Option(givenProject).getOrElse(DecompilerUtil.obtainProject)

    override val expandAliases: Boolean = true
  }

  private object RecursionException extends NoStackTrace
}
