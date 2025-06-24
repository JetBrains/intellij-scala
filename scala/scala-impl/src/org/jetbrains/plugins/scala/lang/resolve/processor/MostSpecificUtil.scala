package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, _}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

class MostSpecificUtil(
  place:      PsiElement,
  argsLength: Int
) {
  private implicit def ctx: ProjectContext = place
  private implicit def context: Context = Context(place)

  def mostSpecificForParameterClause(
    cands: Set[(ScalaResolveResult, Option[ScParameterClause])]
  ): Option[ScalaResolveResult] =
    mostSpecificGeneric(cands.map { case (r, paramClause) =>
      InnerScalaResolveResult(
        r.element,
        implicitConversionClass(r),
        r,
        r.substitutor,
        parameterClause = paramClause
      )
    }, noImplicit = false).map(_.repr)

  def mostSpecificForResolveResult(applicable: Set[ScalaResolveResult]): Option[ScalaResolveResult] =
    mostSpecificGeneric(applicable.map(r =>
      InnerScalaResolveResult(r.element, implicitConversionClass(r), r, r.substitutor)
    ), noImplicit = false).map(_.repr)

  def mostSpecificForImplicitParameters(applicable: Set[ScalaResolveResult]): Option[ScalaResolveResult] =
    mostSpecificGeneric(
      applicable.map(r => toInnerSRR(r, withSubst = true)),
      noImplicit = true
    ).map(_.repr)

  private def toInnerSRR(r: ScalaResolveResult, withSubst: Boolean = false): InnerScalaResolveResult[ScalaResolveResult] =
    InnerScalaResolveResult(
      r.element,
      implicitConversionClass(r),
      r,
      if (withSubst) r.substitutor else ScSubstitutor.empty,
      implicitCase = true
    )

  def nextMostSpecific(rest: Iterable[ScalaResolveResult]): Option[ScalaResolveResult] = {
    nextMostSpecificGeneric(rest.map(toInnerSRR(_))).map(_.repr)
  }

  def isMoreSpecific(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean =
    isMoreSpecific(toInnerSRR(r1), toInnerSRR(r2), checkImplicits = false)

  /**
   * @param parameterClause See [[MethodResolveProcessor]].
   *                        Useful for overloading resolution in scala 3.
   */
  private case class InnerScalaResolveResult[T](
    element:                 PsiNamedElement,
    implicitConversionClass: Option[PsiClass],
    repr:                    T,
    substitutor:             ScSubstitutor,
    parameterClause:         Option[ScParameterClause] = None,
    callByNameImplicit:      Boolean                   = false,
    implicitCase:            Boolean                   = false
  ) {

    def paramsOrCandidateType(
      tp:       ScType,
      undefine: Boolean
    ): Either[Seq[Parameter], ScType] = {
      def parameters(paramsFromType: Seq[Parameter]): Seq[Parameter] =
        parameterClause.fold(paramsFromType)(_.parameters.map(Parameter(_)))

      tp match {
        case ScMethodType(_, params, _) =>
          Left(parameters(params))
        case ScTypePolymorphicType(ScMethodType(_, params, _), typeParams) =>
          if (!undefine) Left(parameters(params))
          else {
            val s = ScSubstitutor.bind(typeParams)(UndefinedType(_))
            Left(
              parameters(params)
                .map(
                  p => p.copy(paramType = s(p.paramType))
                )
            )
          }
        case ScTypePolymorphicType(internal, typeParams) =>
          val existentialSubst = new ExistentialAbstractionBuilder(typeParams).substitutor
          val usedNames        = mutable.Set.empty[String]
          val argumentsBuilder = List.newBuilder[ScExistentialArgument]

          val substedInternal = existentialSubst(internal).visitRecursively {
            case arg: ScExistentialArgument =>
              arg.initialize()
              val name = arg.name

              if (!usedNames.contains(name) && typeParams.exists(_.name == name)) {
                usedNames.add(name)
                argumentsBuilder += arg
              }
            case _ => ()
          }

          Right(
            ScExistentialType(
              substedInternal,
              Option(argumentsBuilder.result()),
              doNotSimplify = true
            )
          )
        case _ => Right(tp)
      }
    }
  }

  private class ExistentialAbstractionBuilder(tparams: Seq[TypeParameter]) {
    private val typeParamIds                                 = tparams.map(_.typeParamId).toSet
    private def doesNotReferenceTparams(tp: ScType): Boolean = !tp.hasRecursiveTypeParameters(typeParamIds)

    private lazy val existentialArgumentSubst: ScSubstitutor = {
      ScSubstitutor.bind(tparams)(
        tp =>
          if (doesNotReferenceTparams(tp.upperType) && doesNotReferenceTparams(tp.lowerType)) {
            ScExistentialArgument(tp.name, tp.typeParameters, tp.lowerType, tp.upperType)
          } else
            ScExistentialArgument.deferred(
              tp.name,
              tp.typeParameters,
              None,
              () => existentialArgumentSubst(tp.lowerType),
              () => existentialArgumentSubst(tp.upperType)
            )
      )
    }

    def substitutor: ScSubstitutor = existentialArgumentSubst
  }



  //todo: make implementation closer to scala.tools.nsc.typechecker.Infer.Inferencer.isAsSpecific
  private def isAsSpecificAs[T](
    lhs:            InnerScalaResolveResult[T],
    rhs:            InnerScalaResolveResult[T],
    checkImplicits: Boolean
  ): Boolean = {
    def isLastParameterRepeated(params: Iterable[Parameter]): Boolean =
      params.lastOption.exists(_.isRepeated)

    def isJavaMethod(m: PsiNamedElement): Boolean =
      !(m.is[ScFunction] || m.isInstanceOf[ScFun] || m.is[ScPrimaryConstructor])

    (lhs.element, rhs.element) match {
      case (lhsElement @ (_: PsiMethod | _: ScFun), rhsElement @ (_: PsiMethod | _: ScFun)) =>
        val lhsType   = lhs.substitutor(getType(lhsElement, lhs.implicitCase))
        val lhsParams = lhs.paramsOrCandidateType(lhsType, undefine = false)
        val rhsType   = rhs.substitutor(getType(rhsElement, rhs.implicitCase))
        val rhsParams = rhs.paramsOrCandidateType(rhsType, undefine = true)

        val paramsConformance = (lhsParams, rhsParams) match {
          case (Left(p1), Left(p2)) =>
            var (params1, params2) = (p1, p2)

            if (
              (
                lhsType.is[ScTypePolymorphicType] && lhsType.is[ScTypePolymorphicType] ||
                  (isJavaMethod(lhsElement) || isJavaMethod(rhsElement))
                ) &&
              (isLastParameterRepeated(params1) ^ isLastParameterRepeated(params2))
            ) return isLastParameterRepeated(params2) //todo: this is hack!!! see SCL-3846, SCL-4048

            if (isLastParameterRepeated(params1) && !isLastParameterRepeated(params2)) params1 = params1.map {
              case p: Parameter if p.isRepeated =>
                implicit val scope: ElementScope = lhs.element.elementScope

                val newParamType = p.paramType match {
                  case ScExistentialType(q, _) => ScExistentialType(q.tryWrapIntoSeqType)
                  case paramType               => paramType.tryWrapIntoSeqType
                }

                Parameter(
                  p.name,
                  p.deprecatedName,
                  newParamType,
                  p.expectedType,
                  p.isDefault,
                  isByName = p.isByName
                )
              case p => p
            }

            val numberOfRepeatedArgsPassed =
              if (params1.nonEmpty) 0.max(argsLength - params1.length)
              else                  0

            val repeatedParamExpr =
              Expression(
                if (params1.nonEmpty) params1.last.paramType
                else                  Nothing,
                place
              )

            val argExprs =
              params1.map(p => Expression(p.paramType, place)) ++
                Seq.fill(numberOfRepeatedArgsPassed)(repeatedParamExpr)

            Compatibility.checkConformance(params2, argExprs, checkImplicits)
          case (Right(lhsType), Right(rhsType)) =>
            lhsType.conforms(rhsType, ConstraintSystem.empty) //todo: with implicits?
          case (Left(_), Right(_)) if !lhs.implicitCase => return false
          case _                                        => return true
        }

        paramsConformance match {
          case cs @ ConstraintSystem(uSubst) =>
            var u = cs
            rhsType match {
              case ScTypePolymorphicType(_, typeParams) =>
                val typeParamIds = typeParams.map(_.typeParamId).toSet

                typeParams.foreach { tp =>
                  val typeParamId = tp.typeParamId

                  tp.lowerType match {
                    case lower if lower.isNothing || lower.hasRecursiveTypeParameters(typeParamIds) =>
                    case lower =>
                      u =
                        u.withLower(typeParamId, uSubst(lower))
                          .withTypeParamId(typeParamId)
                  }

                  tp.upperType match {
                    case upper if upper.isAny || upper.hasRecursiveTypeParameters(typeParamIds) =>
                    case upper =>
                      u =
                        u.withUpper(typeParamId, uSubst(upper))
                          .withTypeParamId(typeParamId)
                  }
                }
              case _ =>
            }

            ConstraintSystem.unapply(u).isDefined
          case _ => false
        }
      case (_, _: PsiMethod) => true
      case (lhsElement, rhsElement) =>
        val lhsType = getType(lhsElement, lhs.implicitCase)
        val rhsType = getType(rhsElement, rhs.implicitCase)
        //@TODO: similarly to the case above this should probably take implicit conversions into account
        lhsType.conforms(rhsType)
    }
  }

  private def getContainingClass(element: PsiNamedElement): Option[PsiClass] =
    for {
      member <- element.nameContext.asOptionOf[PsiMember]
      cls    <- member.containingClass.toOption
    } yield cls

  private def extractContainingClass(res: InnerScalaResolveResult[_]): Option[PsiClass] =
    getContainingClass(res.element)

  /**
   * 1) `c1` is a subclass of `c2`, or
   * 2) `c1` is a companion object of a class derived from `c2`, or
   * 3) `c2` is a companion object of a class from which `c1` is derived.
   */
  private def isDerived(cls: PsiClass, base: PsiClass): Boolean = {
    if (cls == base)                                  false
    else if (ScalaPsiUtil.isInheritorDeep(cls, base)) true
    else
      (cls, base) match {
        case (obj: ScObject, _) =>
          val companionClass = ScalaPsiUtil.getCompanionModule(obj)
          companionClass.exists(isDerived(_, base))
        case (_, baseObj: ScObject) =>
          val baseObjectCompanionClass = ScalaPsiUtil.getCompanionModule(baseObj)
          baseObjectCompanionClass.exists(isDerived(cls, _))
        case _ => false
      }
  }

  def isInMoreSpecificClass(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean =
    (getContainingClass(r1.element), getContainingClass(r2.element)) match {
      case (Some(clazz1), Some(clazz2)) =>
        clazz1.qualifiedName != clazz2.qualifiedName &&
          ScalaPsiUtil.isInheritorDeep(clazz1, clazz2) &&
          !ScalaPsiUtil.isInheritorDeep(clazz2, clazz1)
      case _ => false
    }

  private def relativeWeight[T](r1: InnerScalaResolveResult[T], r2: InnerScalaResolveResult[T],
                                checkImplicits: Boolean): Int = {
    val asSpecific = if (isAsSpecificAs(r1, r2, checkImplicits)) 1 else 0

    val derived =
      (
        for {
          cls1 <- extractContainingClass(r1)
          cls2 <- extractContainingClass(r2)
        } yield
          if (isDerived(cls1, cls2)) 1
          else                       0
        ).getOrElse(0)

    asSpecific + derived
  }

  private def isMoreSpecific[T](
    r1:             InnerScalaResolveResult[T],
    r2:             InnerScalaResolveResult[T],
    checkImplicits: Boolean
  ): Boolean = {
    def hasImplicitParameters(isrr: InnerScalaResolveResult[T]): Boolean =
      isrr.element match {
        case fn: ScFunction =>
          //@TODO: parameterClausesWithExtension.flatMap(_.effectiveParameters)?
          fn.parameters.exists(_.isImplicit)
        case _              => false
      }

    ProgressManager.checkCanceled()

    (r1.implicitConversionClass, r2.implicitConversionClass) match {
      case (Some(t1), Some(t2)) if ScalaPsiUtil.isInheritorDeep(t1, t2) => true
      case _ =>
        if (r1.callByNameImplicit ^ r2.callByNameImplicit) !r1.callByNameImplicit
        else {
          val weightR1R2 = relativeWeight(r1, r2, checkImplicits)
          val weightR2R1 = relativeWeight(r2, r1, checkImplicits)

          /**
           * Scala 3 expands of the notion of specificity (SLS §6.26.3) in the following ways:
           * 1. If the relative weights are the same, and `r1` takes implicit/context
           *    parameters, while `r2` does not, `r1` is considered more specific.
           * 2. If the relative weights are the same, and both `r1` and  `r2` take implicit/context
           *    parameters, relative weights are recomputed and compared as if `r1` and `r2`
           *    take normal (non-implicit) parameters.
           */
          if (place.isInScala3File && weightR1R2 == weightR2R1 && r1.implicitCase) {
            val r1HasImplicitParameters = hasImplicitParameters(r1)
            val r2HasImplicitParameters = hasImplicitParameters(r2)

            if (!r1HasImplicitParameters && r2HasImplicitParameters)
              true
            else if (r1HasImplicitParameters && r2HasImplicitParameters) {
              val r1withParams = r1.copy(implicitCase = false)
              val r2withParams = r2.copy(implicitCase = false)

              val weightWithParamsR1R2 = relativeWeight(r1withParams, r2withParams, checkImplicits)
              val weightWithParamsR2R1 = relativeWeight(r2withParams, r1withParams, checkImplicits)

              weightWithParamsR1R2 > weightWithParamsR2R1
            } else false
          } else weightR1R2 > weightR2R1
        }
    }
  }

  private def mostSpecificGeneric[T](
    applicable: Set[InnerScalaResolveResult[T]],
    noImplicit: Boolean
  ): Option[InnerScalaResolveResult[T]] = {
    def calc(checkImplicits: Boolean): Option[InnerScalaResolveResult[T]] = {
      val a1iterator = applicable.iterator

      while (a1iterator.hasNext) {
        val a1 = a1iterator.next()
        var break = false
        val a2iterator = applicable.iterator
        while (a2iterator.hasNext && !break) {
          val a2 = a2iterator.next()
          if (a1 != a2 && !isMoreSpecific(a1, a2, checkImplicits)) break = true
        }
        if (!break) return Option(a1)
      }

      None
    }

    val result = calc(checkImplicits = false)

    if (!noImplicit && result.isEmpty)
      calc(checkImplicits = true)
    else
      result
  }

  private def nextMostSpecificGeneric[T](rest: Iterable[InnerScalaResolveResult[T]]): Option[InnerScalaResolveResult[T]] =
    if (rest.isEmpty)        None
    else if (rest.size == 1) Option(rest.head)
    else  {
      val iter     = rest.iterator
      var foundMax = iter.next()

      while (iter.hasNext) {
        val res = iter.next()

        for {
          cls1 <- extractContainingClass(res)
          cls2 <- extractContainingClass(foundMax)
        } if (isDerived(cls1, cls2)) foundMax = res
      }

      Option(foundMax)
    }

  def getType(e: PsiNamedElement, implicitCase: Boolean): ScType = {
    val res = e match {
      case m: PsiMethod =>
        val scope = place.elementScope
        m.methodTypeProvider(scope).polymorphicType(
          dropExtensionClauses = true, //@TODO: should probably be srr.isExtensionCall
        )
      case fun: ScFun => fun.polymorphicType()
      case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
        case pd: ScPatternDefinition if PsiTreeUtil.isContextAncestor(pd, place, true) =>
          pd.declaredType.getOrElse(Nothing)
        case vd: ScVariableDefinition if PsiTreeUtil.isContextAncestor(vd, place, true) =>
          vd.declaredType.getOrElse(Nothing)
        case _ => refPatt.`type`().getOrAny
      }
      case typed: ScTypedDefinition => typed.`type`().getOrAny
      case f: PsiField              => f.getType.toScType()
      case _                        => Nothing
    }

    res match {
      case ScMethodType(retType, _, true) if implicitCase => retType
      case ScTypePolymorphicType(ScMethodType(retType, _, true), typeParameters) if implicitCase =>
        ScTypePolymorphicType(retType, typeParameters)
      case tp => tp
    }
  }

  private def implicitConversionClass(srr: ScalaResolveResult): Option[ScTemplateDefinition] =
    for {
      conversion <- srr.implicitConversion
      member     <- conversion.element.nameContext.asOptionOf[ScMember]
      psiClass   <- Option(member.containingClass)
    } yield psiClass
}

object MostSpecificUtil {
  def apply(
    place:      PsiElement,
    argsLength: Int,
  ): MostSpecificUtil = new MostSpecificUtil(place, argsLength)
}