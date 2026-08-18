package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.types.ScMatchType.MatchTypeCase
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.annotation.tailrec
import scala.collection.immutable.{ArraySeq, SeqMap}
import scala.util.control.NoStackTrace


case class ScMatchType private (
  scrutinee:  ScType,
  cases:      Seq[MatchTypeCase],
  upperBound: Option[ScType]
) extends ScalaType with ValueType {
  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitMatchType(this)

  override implicit def projectContext: ProjectContext = scrutinee.projectContext

  def reduce: TypeResult = ScMatchType.reduce(this)

  private def checkCases(
    cases:       Seq[MatchTypeCase],
    otherCases:  Seq[MatchTypeCase],
    constraints: ConstraintSystem,
    falseUndef:  Boolean
  ): ConstraintsResult =
    if (cases.size != otherCases.size) ConstraintsResult.Left
    else {
      var combinedConstraints: ConstraintsResult = constraints

      val lIter = cases.iterator
      val rIter = otherCases.iterator

      while (combinedConstraints.isRight && lIter.hasNext) {
        val (lPat, lBody) = lIter.next().apply()
        val (rPat, rBody) = rIter.next().apply()

        val patEquiv = lPat.equiv(rPat, combinedConstraints.constraints)

        patEquiv match {
          case ConstraintsResult.Left =>
            combinedConstraints = ConstraintsResult.Left
          case patCs: ConstraintSystem =>
            combinedConstraints = combinedConstraints.constraints + patCs
        }

        combinedConstraints = lBody.equiv(rBody, combinedConstraints.constraints, falseUndef)
      }

      combinedConstraints
    }

  override def equivInner(
    other:       ScType,
    constraints: ConstraintSystem,
    falseUndef:   Boolean
  )(implicit
    context: Context
  ): ConstraintsResult = other match {
    case mt: ScMatchType =>
      val scrutineeEquiv = scrutinee.equiv(mt.scrutinee, constraints, falseUndef = false)

      scrutineeEquiv match {
        case ConstraintsResult.Left => ConstraintsResult.Left
        case cs: ConstraintSystem   => checkCases(cases, mt.cases, cs, falseUndef)
      }
    case other =>
      //Match type and its redex are mutual subtypes
      reduce.fold(
        _ => ConstraintsResult.Left,
        _.equivInner(other, constraints, falseUndef)
      )
  }
}

object ScMatchType {
  object Reduced {
    def unapply(tpe: ScMatchType): Option[ScType] = tpe.reduce.toOption
  }

  type MatchTypeCase = () => (ScType, ScType)

  private val maxRecursionDepth = 50

  private def reductionCache(project: Project): ConcurrentMap[ScType, ContextDependent[TypeResult]] =
    project.getService(classOf[MatchTypeReductionCacheService]).reductionCache

  def clearReductionCache(project: Project): Unit = reductionCache(project).clear()

  @Service(Array(Service.Level.PROJECT))
  private final class MatchTypeReductionCacheService {
    val reductionCache: ConcurrentMap[ScType, ContextDependent[TypeResult]] = new ConcurrentHashMap()
  }

  def reduce(tpe: ScMatchType): TypeResult = {
    val cache           = reductionCache(tpe.projectContext)
    val resultInContext = Option(cache.get(tpe)).getOrElse(new ContextDependent())

    //@TODO: smart caching of intermediate results?
    val result = resultInContext.get.getOrElse {
      try {
        val (value, valueInContext) =
          resultInContext.updatedUsing(ctx => reduce(tpe, 0)(ctx))

        cache.put(tpe, valueInContext)
        value
      } catch {
        case _: MaximumRecursionDepthExceeded =>
          Failure(
            ScalaBundle.message("match.type.recursion.depth.exceeded", tpe.canonicalText)
          )(tpe.projectContext)
      }
    }

    result
  }

  def reduce(
    tpe:     ScMatchType,
    depth:   Int
  )(implicit
    ctx: Context
  ): TypeResult =
    if (depth >= maxRecursionDepth) throw new MaximumRecursionDepthExceeded
    else {
      implicit val pc: ProjectContext = tpe.projectContext

      val scrutinee = tpe.scrutinee

      def matchCase(pat: ScType): MatchResult = {
        val typeVarsBuilder = ArraySeq.newBuilder[TypeParameter]

        def isTypeVar(tpt: TypeParameterType): Boolean = {
          val firstChar = tpt.name.charAt(0)
          firstChar.isLower || firstChar == '_'
        }

        pat.visitRecursively {
          case tpt: TypeParameterType if isTypeVar(tpt) => typeVarsBuilder += tpt.typeParameter
          case _                                        => ()
        }

        val typeVars    = typeVarsBuilder.result()
        val undefSubst  = ScSubstitutor.undefineTypeParams(typeVars)
        val conformance = scrutinee.conforms(undefSubst(pat), ConstraintSystem.empty)

        conformance match {
          case ConstraintSystem(subst) => MatchResult.Reduced(subst)
          case _ =>
            if (isProvablyDisjoint(pat, scrutinee)) MatchResult.Disjoint
            else                                    MatchResult.Stuck
        }
      }

      @tailrec
      def aux(
        remainingCases: Iterator[MatchTypeCase],
      ): TypeResult =
        if (remainingCases.isEmpty)
          Failure(
            ScalaBundle.message(
              "match.type.no.cases.match.scrutinee",
              scrutinee.canonicalText
            )
          )
        else {
          val (casePat, caseRes) = remainingCases.next().apply()
          val matchResult        = matchCase(casePat)

          matchResult match {
            case MatchResult.Reduced(subst) =>
              val substedRes = subst(caseRes)
              //TODO: indirect aliases to match types
              val dealiased = substedRes.removeAliasDefinitions()
              var currentDepth = depth

              val reducedRec = dealiased.updateRecursively {
                case mt: ScMatchType =>
                  currentDepth += 1
                  val redex = reduce(mt, currentDepth)
                  redex.getOrElse(mt)
              }

              Right(reducedRec)
            case MatchResult.Stuck =>
              Failure(
                ScalaBundle.message(
                  "match.type.non.disjoint.case",
                  scrutinee.canonicalText,
                  casePat.canonicalText
                )
              )
            case MatchResult.Disjoint =>
              aux(remainingCases)
          }
        }

      aux(tpe.cases.iterator)
    }

  private class MaximumRecursionDepthExceeded extends NoStackTrace

  sealed trait MatchResult
  object MatchResult {
    case class Reduced(subst: ScSubstitutor) extends MatchResult
    case object Disjoint                     extends MatchResult
    case object Stuck                        extends MatchResult
  }

  private def isProvablyDisjoint(lhs: PsiClass, rhs: PsiClass): Boolean = {
    def eitherDerivesFromAnother(l: PsiClass, r: PsiClass): Boolean =
      ScEquivalenceUtil.areClassesEquivalent(l, r) ||
        ScalaPsiUtil.isInheritorDeep(l, r) ||
        ScalaPsiUtil.isInheritorDeep(r, l)

    def smallestBaseNonTrait(cls: PsiClass): PsiClass =
      MixinNodes.allSuperClasses(cls).find {
        case _: ScTrait => false
        case _          => true
      }.get

    if (eitherDerivesFromAnother(lhs, rhs))
      false
    else if (lhs.hasModifier(JvmModifier.FINAL) || rhs.hasModifier(JvmModifier.FINAL))
      true
    else if (!eitherDerivesFromAnother(smallestBaseNonTrait(lhs), smallestBaseNonTrait(rhs)))
      true
    else if (lhs.isSealed) lhs.directInheritorsOfSealed.forall(isProvablyDisjoint(_, rhs))
    else if (rhs.isSealed) rhs.directInheritorsOfSealed.forall(isProvablyDisjoint(_, lhs))
    else                   false
  }

  private def isProvablyDisjointTypeArgs(
    lhsArgs: Seq[ScType],
    rhsArgs: Seq[ScType],
    tparams: Seq[TypeParameter]
  ): Boolean = {
    lhsArgs.lazyZip(rhsArgs).lazyZip(tparams).exists { case (l, r, tparam) =>
      if (tparam.isCovariant)          isProvablyDisjoint(l, r) /* && hasFieldOfType(cls, tparam) @TODO */
      else if (tparam.isContravariant) false
      else                             isProvablyDisjoint(l, r)
    }
  }

  /**
   * The notion of disjointness is intuitively based on the following properties of scala language:
   * 1. Single inheritance of classes
   * 2. Inability to inherit from final classes
   * 3. Sealed traits having a known set of direct inheritors
   * 4. Constant types/singleton paths with distinct values are disjoint
   */
  private def isProvablyDisjoint(lhs: ScType, rhs: ScType): Boolean = {
    @tailrec
    def disjointnessBoundary(tpe: ScType): ScType = tpe match {
      case downer: DesignatorOwner =>
        downer.extractDesignatorSingleton match {
          case Some(extracted) => disjointnessBoundary(extracted)
          case None            => downer
        }
      case TypeConstructor(etaExpansion)    => etaExpansion
      case tpt: TypeParameterType           => disjointnessBoundary(tpt.upperType)
      case AliasType(_, _, Right(upper), _) => disjointnessBoundary(upper)
      case mt: ScMatchType                  => mt.reduce.toOption.orElse(mt.upperBound).getOrElse(mt)
      case tp                               => tp
    }

    def isBaseClassWithDisjointArgs(
      cls:      PsiClass,
      lhsSubst: ScSubstitutor,
      rhsSubst: ScSubstitutor
    ): Boolean = {
      val typeParams = cls.getTypeParameters.instantiate
      val superType  = ScParameterizedType(ScalaType.designator(cls), typeParams.map(TypeParameterType(_)))

      (lhsSubst(superType), rhsSubst(superType)) match {
        case (ParameterizedType(_, lhsArgs), ParameterizedType(_, rhsArgs)) =>
          isProvablyDisjointTypeArgs(lhsArgs, rhsArgs, typeParams)
        case _ => false
      }
    }

    def haveCommonBaseWithDisjointArgs(
      lhs:              PsiClass,
      lhsTypeArgsSubst: ScSubstitutor,
      rhs:              PsiClass,
      rhsTypeArgsSubst: ScSubstitutor
    ): Boolean = {
      val rhsBaseClasses = SeqMap(rhs -> rhsTypeArgsSubst) ++ MixinNodes.allSuperClassesWithSubst(rhs)
      val lhsBaseClasses = SeqMap(lhs -> lhsTypeArgsSubst) ++ MixinNodes.allSuperClassesWithSubst(lhs)

      val commonBaseClasses =
        lhsBaseClasses.filter {
          case (cls, _) => rhsBaseClasses.contains(cls)
        }

      def isAncestorToAnotherCommonBaseClass(ancestorCls: PsiClass): Boolean =
        commonBaseClasses.exists {
          case (childCls, _) =>
            childCls != ancestorCls &&
              ScalaPsiUtil.isInheritorDeep(childCls, ancestorCls)
        }

      commonBaseClasses.exists { case (baseClass, lhsSubst) =>
        !isAncestorToAnotherCommonBaseClass(baseClass) &&
          isBaseClassWithDisjointArgs(
            baseClass,
            lhsSubst.followed(lhsTypeArgsSubst),
            rhsBaseClasses(baseClass).followed(rhsTypeArgsSubst)
          )
      }
    }

    (disjointnessBoundary(lhs), disjointnessBoundary(rhs)) match {
      case (lhs, rhs) if lhs.isAny || rhs.isAny => false
      case (lhs, rhs) if lhs.isNothing || rhs.isNothing || lhs.isAnyKind || rhs.isAnyKind => true
      case (lhs: ValType, rhs) if lhs != rhs => true
      case (lhs, rhs: ValType) if lhs != rhs => true
      case (lhs, ScOrType(orLhs, orRhs)) =>
        isProvablyDisjoint(lhs, orLhs) && isProvablyDisjoint(lhs, orRhs)
      case (lhs, ScAndType(andLhs, andRhs)) =>
        isProvablyDisjoint(lhs, andLhs) || isProvablyDisjoint(lhs, andRhs)
      case (ScTypePolymorphicType(lhsRes, lhsParams), ScTypePolymorphicType(rhsRes, rhsParams)) =>
        lhsParams.size == rhsParams.size && isProvablyDisjoint(lhsRes, rhsRes)
      case (_: ScTypePolymorphicType, _)                      => true
      case (_, _: ScTypePolymorphicType)                      => true
      case (ScLiteralType(lhsValue), ScLiteralType(rhsValue)) => lhsValue != rhsValue
      case (lhs: DesignatorOwner, rhs: DesignatorOwner) if lhs.isSingleton && rhs.isSingleton =>
        lhs.element != rhs.element
      case (DesignatorOwner(lhsCls: PsiClass), DesignatorOwner(rhsCls: PsiClass)) =>
        isProvablyDisjoint(lhsCls, rhsCls)
      case (lhs @ ExtractClass(lhsCls), rhs @ ExtractClass(rhsCls)) =>
        val lhsSubst = lhs.removeAliasDefinitions() match {
          case ParameterizedType(_, targs) => ScSubstitutor.bind(lhsCls.getTypeParameters.instantiate, targs)
          case _                           => ScSubstitutor.empty
        }

        val rhsSubst = rhs.removeAliasDefinitions() match {
          case ParameterizedType(_, targs) => ScSubstitutor.bind(rhsCls.getTypeParameters.instantiate, targs)
          case _                           => ScSubstitutor.empty
        }

        isProvablyDisjoint(lhsCls, rhsCls) ||
          haveCommonBaseWithDisjointArgs(lhsCls, lhsSubst, rhsCls, rhsSubst)
      case _ => false
    }
  }
}
