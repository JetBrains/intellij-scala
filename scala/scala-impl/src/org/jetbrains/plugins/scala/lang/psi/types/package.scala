package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation.shouldExpand
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType.Name
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, _}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{NonValueType, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.{ProjectContext, _}
import org.jetbrains.plugins.scala.util.SAMUtil
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

import scala.util.control.NoStackTrace
/**
  * @author adkozlov
  */
package object types {

  implicit class ScTypeExt(private val scType: ScType) extends AnyVal {
    private def typeSystem = scType.typeSystem
    private def projectContext = scType.projectContext
    private def stdTypes = projectContext.stdTypes

    def equiv(`type`: ScType): Boolean = {
      typeSystem.equiv(scType, `type`)
    }

    def equiv(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean = true): ConstraintsResult = {
      typeSystem.equivInner(scType, `type`, constraints, falseUndef)
    }

    def conforms(`type`: ScType): Boolean = {
      typeSystem.conformsInner(`type`, scType).isRight
    }

    def weakConforms(`type`: ScType): Boolean = {
      typeSystem.conformsInner(`type`, scType, checkWeak = true).isRight
    }

    def conformanceSubstitutor(`type`: ScType): Option[ScSubstitutor] = {
      implicit val context: ProjectContext = `type`.projectContext
      conforms(`type`, ConstraintSystem.empty) match {
        case ConstraintSystem(substitutor) => Some(substitutor)
        case _ => None
      }
    }

    /** see scala.tools.nsc.typechecker.Infer.Inferencer#isConservativelyCompatible from scalac */
    def isConservativelyCompatible(pt: ScType): ConstraintsResult = {
      def tryConformanceNoParams(fullResults: ConstraintsResult): ConstraintsResult = scType match {
        case ScMethodType(retTpe, ps, _) if ps.isEmpty => retTpe.conforms(pt, ConstraintSystem.empty, checkWeak = true)
        case FunctionType(retTpe, ps)    if ps.isEmpty => retTpe.conforms(pt, ConstraintSystem.empty, checkWeak = true)
        case _                                         => fullResults
      }

      if (pt.isUnit) ConstraintSystem.empty // can perform unit coercion
      else {
        val conformanceResult = conforms(pt, ConstraintSystem.empty, checkWeak = true)
        if (conformanceResult.isRight) conformanceResult
        else                           tryConformanceNoParams(conformanceResult)
      }
    }

    def conforms(`type`: ScType,
                 constraints: ConstraintSystem,
                 checkWeak: Boolean = false): ConstraintsResult = {
      typeSystem.conformsInner(`type`, scType, constraints = constraints, checkWeak = checkWeak)
    }

    def glb(`type`: ScType, checkWeak: Boolean = false): ScType = {
      typeSystem.glb(scType, `type`, checkWeak)
    }

    def lub(`type`: ScType, checkWeak: Boolean = true): ScType = {
      typeSystem.lub(scType, `type`, checkWeak)
    }

    private def isStdType(name: String) = scType match {
      case StdType(`name`, _) => true
      case _ => false
    }

    def isBoolean: Boolean = isStdType(Name.Boolean)

    def isAny: Boolean = isStdType(Name.Any)

    def isAnyRef: Boolean = isStdType(Name.AnyRef)

    def isAnyVal: Boolean = isStdType(Name.AnyVal)

    def isNothing: Boolean = isStdType(Name.Nothing)

    def isUnit: Boolean = isStdType(Name.Unit)

    def isNull: Boolean = isStdType(Name.Null)

    def isPrimitive: Boolean = scType match {
      case v: ValType => !isUnit
      case _ => false
    }

    def removeUndefines(): ScType = scType.updateLeaves {
      case _: UndefinedType => stdTypes.Any
    }

    /**
      * This method is important for parameters expected type.
      * There shouldn't be any abstract type in this expected type.
      **/
    def removeAbstracts: ScType = scType.updateLeaves {
      case a: ScAbstractType => a.simplifyType
    }

    def removeVarianceAbstracts(): ScType = {
      var index = 0
      scType.recursiveVarianceUpdate() {
        case (ScAbstractType(_, lower, upper), v) =>
          v match {
            case Contravariant => ReplaceWith(lower)
            case Covariant     => ReplaceWith(upper)
            case Invariant     =>
              index += 1
              ReplaceWith(ScExistentialArgument(s"_$$$index", Nil, lower, upper))
          }
        case _ => ProcessSubtypes
      }.unpackedType
    }

    def toPsiType: PsiType = typeSystem.toPsiType(scType)

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

    def extractClassType: Option[(PsiClass, ScSubstitutor)] = {
      new ClassTypeExtractor(needSubstitutor = true)
        .extractFrom(scType)
    }

    def extractClass: Option[PsiClass] = {
      new ClassTypeExtractor(needSubstitutor = false)
        .extractFrom(scType).map(_._1)
    }

    //performance critical method!
    //may return None even if extractClass is not empty
    @scala.annotation.tailrec
    final def extractClassSimple(visited: Set[ScTypeAlias] = Set.empty): Option[PsiClass] = scType match {
      case ScDesignatorType(c: PsiClass) => Some(c)
      case _: StdType => None
      case ParameterizedType(des, _) => des.extractClassSimple(visited)
      case ScProjectionType(_, c: PsiClass) => Some(c)
      case ScProjectionType(_, ta: ScTypeAliasDefinition) if !visited.contains(ta) => ta.aliasedType.toOption match {
        case Some(t) => t.extractClassSimple(visited + ta.physical)
        case _ => None
      }
      case _ => None
    }

    //performance critical method!
    def canBeSameOrInheritor(t: ScType): Boolean = checkSimpleClasses(t,
      (c1, c2) => c1.sameOrInheritor(c2)
    )

    //performance critical method!
    def canBeSameClass(t: ScType): Boolean = checkSimpleClasses(t, areClassesEquivalent)

    private def checkSimpleClasses(t: ScType, condition: (PsiClass, PsiClass) => Boolean) = {
      (scType.extractClassSimple(), t.extractClassSimple()) match {
        case (Some(c1), Some(c2)) if !condition(c1, c2) => false
        case _ => true
      }
    }

    def removeAliasDefinitions(expandableOnly: Boolean = false): ScType = {
      def needExpand(ta: ScTypeAliasDefinition) = !expandableOnly || shouldExpand(ta)

      def innerUpdate(tp: ScType, visited: Set[ScType]): ScType = {
        tp.recursiveUpdate {
          case AliasType(_: ScTypeAliasDefinition, Right(_: ScTypePolymorphicType), _) => ProcessSubtypes
          case AliasType(ta: ScTypeAliasDefinition, _, Failure(_)) if needExpand(ta) =>
            ReplaceWith(projectContext.stdTypes.Any)
          case `type`@AliasType(ta: ScTypeAliasDefinition, _, Right(upper)) if needExpand(ta) =>
            if (visited.contains(`type`)) throw RecursionException
            val updated =
              try innerUpdate(upper, visited + `type`)
              catch {
                case RecursionException =>
                  if (visited.nonEmpty) throw RecursionException
                  else `type`
              }
            ReplaceWith(updated)
          case _ => ProcessSubtypes
        }
      }

      innerUpdate(scType, Set.empty)
    }

    def widen: ScType = scType match {
      case lit: ScLiteralType if lit.allowWiden => lit.wideType
      case other                                => other.tryExtractDesignatorSingleton
    }

    def extractDesignatorSingleton: Option[ScType] = scType match {
      case desinatorOwner: DesignatorOwner => desinatorOwner.designatorSingletonType
      case _ => None
    }

    def tryExtractDesignatorSingleton: ScType = extractDesignatorSingleton.getOrElse(scType)

    def wrapIntoSeqType(implicit scope: ElementScope): Option[ScType] =
      scope.scalaSeqType.map(ScParameterizedType(_, Seq(scType)))

    def tryWrapIntoSeqType(implicit scope: ElementScope): ScType =
      wrapIntoSeqType.getOrElse(scType)

    def hasRecursiveTypeParameters[T](typeParamIds: Set[Long]): Boolean = scType.subtypeExists {
      case tpt: TypeParameterType =>
        typeParamIds.contains(tpt.typeParamId)
      case _ => false
    }

    def widenIfLiteral: ScType = scType match {
      case litTy: ScLiteralType => litTy.wideType
      case _ => scType
    }
  }

  implicit class ScalaSeqExt(private val context: PsiElement) extends AnyVal {
    @CachedInUserData(
      context,
      ScalaPsiManager.instance(context.getProject()).TopLevelModificationTracker
    )
    def scalaSeqFqn: String =
      if (context.newCollectionsFramework) "scala.collection.immutable.Seq"
      else                                 "scala.collection.Seq"
  }

  implicit class ScTypesExt(private val types: Seq[ScType]) extends AnyVal {
    def glb(checkWeak: Boolean = false)(implicit project: ProjectContext): ScType = {
      project.typeSystem.glb(types, checkWeak)
    }

    def lub(checkWeak: Boolean = true)(implicit project: ProjectContext): ScType = {
      project.typeSystem.lub(types, checkWeak)
    }
  }

  private trait Extractor[T <: PsiNamedElement] {
    def filter(named: PsiNamedElement, subst: ScSubstitutor): Option[(T, ScSubstitutor)]
    def expandAliases: Boolean
    def needSubstitutor: Boolean

    def extractFrom(scType: ScType,
                    visitedAliases: Set[ScTypeAlias] = Set.empty): Option[(T, ScSubstitutor)] = {

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
                  extractFrom(actualSubst(des), visitedAliases + definition.physical)
                case Some(tp) =>
                  extractFrom(actualSubst(tp), visitedAliases + definition.physical)
                case _ => None
              }
            case _ => filter(actualElement, actualSubst)
          }
        case designatorOwner: DesignatorOwner =>
          designatorOwner.element match {
            case definition: ScTypeAliasDefinition if needExpand(definition) =>
              definition.aliasedType.toOption.flatMap {
                extractFrom(_, visitedAliases + definition.physical)
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
          stdType.syntheticClass.flatMap {
            filter(_, ScSubstitutor.empty)
          }
        case ScExistentialType(quantified, _) =>
          extractFrom(quantified, visitedAliases)
        case TypeParameterType.ofPsi(psiTypeParameter) =>
          filter(psiTypeParameter, ScSubstitutor.empty)
        case _ => None
      }
    }
  }

  private class DesignatorExtractor(override val expandAliases: Boolean, override val needSubstitutor: Boolean) extends Extractor[PsiNamedElement] {
    override def filter(named: PsiNamedElement, subst: ScSubstitutor): Option[(PsiNamedElement, ScSubstitutor)] =
      Some(named, subst)
  }

  private class ClassTypeExtractor(override val needSubstitutor: Boolean) extends Extractor[PsiClass] {
    override def filter(named: PsiNamedElement, subst: ScSubstitutor): Option[(PsiClass, ScSubstitutor)] =
      named match {
        case _: PsiTypeParameter => None
        case c: PsiClass => Some(c, subst)
        case _ => None
      }

    override val expandAliases: Boolean = true
  }

  private object RecursionException extends NoStackTrace

  /**
   * Extractor for types, which are function-like
   * (i.e. FunctionN types, PartialFunction types and SAM traits).
   * See also: [[FunctionTypeMarker]]
   */
  final case class FunctionLikeType(place: PsiElement) {
    import FunctionTypeMarker._

    def unapply(tpe: ScType): Option[(FunctionTypeMarker, ScType, Seq[ScType])] = tpe match {
      case FunctionType(retTpe, paramTpes)       => (FunctionN, retTpe, paramTpes).toOption
      case PartialFunctionType(retTpe, paramTpe) => (PF, retTpe, Seq(paramTpe)).toOption
      case ScAbstractType(_, _, upper)           => unapply(upper)
      case tpe if place.isSAMEnabled             =>
        for {
          (_, retTpe, paramTpes) <- SAMUtil.toSAMType(tpe, place).flatMap(unapply)
          cls                    <- tpe.extractClass
        } yield (SAM(cls), retTpe, paramTpes)
      case _ => None
    }
  }

  sealed trait FunctionTypeMarker
  object FunctionTypeMarker {
    case object FunctionN         extends FunctionTypeMarker
    case object PF                extends FunctionTypeMarker
    case class SAM(cls: PsiClass) extends FunctionTypeMarker

    private[this] def priority(marker: FunctionTypeMarker): Int = marker match {
      case PF        => 2
      case FunctionN => 1
      case _: SAM    => 0
    }

    implicit val markerOrdering: Ordering[FunctionTypeMarker] = Ordering.by(priority)
  }

  object FullyAbstractType {
    def unapply(abs: ScAbstractType): Boolean = abs.upper.isAny && abs.lower.isNothing
  }
}
