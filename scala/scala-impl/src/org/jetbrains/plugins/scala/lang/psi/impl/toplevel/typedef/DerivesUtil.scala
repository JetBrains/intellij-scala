package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{Model, ObjectExt, StringsExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScDerivesClauseOwner, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.TypeVariableUnification
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor

object DerivesUtil {
  /**
   * Check if ADT can be unified with typeclass' type parameter and
   * produce text of a given definition, which should be put into
   * ADT's companion object. NOTE: `refName` is not necessarily equal to
   * `tc.name`, it may be aliased.
   * Derivation mechanism covers two cases (a) and (b):
   * (a) ADT and type class parameters overlap on the right and have the
   *     same kinds at the overlap.
   *     {{{
   *       trait TC[F[X, Y]]
   *       case class Foo1[A, B, C] derives TC
   *       // given derived$TC[A]: TC[[x, y] =>> Foo1[A, x, y]]
   *
   *       case class Foo2[A, B] derives TC
   *       // natural case, given derived$TC: TC[Foo2]
   *
   *       case class Foo3[A] derives TC
   *       // given derived$TC: TC[[x, y] =>> Foo3[y]]
   *
   *       case class Foo4 derives TC
   *       // given derived$TC: TC[[x, y] =>> Foo4]
   *     }}}
   * (b) The type class type parameter and all ADT type parameters are of kind 'Type'
   *
   *     In this case the ADT has at least one type parameter of kind 'Type',
   *     otherwise it would already have been covered as a "natural" case
   *     for a type class of the form F[_].
   *
   *     The derived instance has a type parameter and a given for
   *     each of the type parameters of the ADT.
   *     {{{
   *     trait TC[T]
   *     case class C[A, B, C] derives TC
   *     //given derived$TC[A, B, C](given TC[A], TC[B], TC[C]): TC[C[A, B, C]]
   *     }}}
   */
  def deriveSingleParameterTypeClass(
    refName: String,
    tc:      ScTypeDefinition,
    owner:   ScDerivesClauseOwner
  ): Option[String] = {
    if (tc.typeParameters.size != 1) None
    else {
      val derivedFqn                = s"${tc.qualifiedName}.derived"
      val typeClassParamType        = TypeParameter(tc.typeParameters.head)
      val instanceTypeParams        = typeClassParamType.typeParameters
      val instanceArity             = instanceTypeParams.size
      val ownerTypeParams           = owner.typeParameters.map(TypeParameter(_))
      val ownerArity                = ownerTypeParams.size
      val alignedOwnerTypeParams    = ownerTypeParams.takeRight(instanceArity)
      val alignedInstanceTypeParams = instanceTypeParams.takeRight(alignedOwnerTypeParams.length)

      if ((instanceArity > 0 || instanceArity == ownerArity) &&
        TypeVariableUnification.unifiableKinds(alignedOwnerTypeParams, alignedInstanceTypeParams)) {
        // case (a)
        val nonOverlappingOwnerParams =
          ownerTypeParams.dropRight(instanceArity).map(_.name)

        val resultTypeText =
          if (instanceArity == ownerArity) s"${tc.qualifiedName}[${owner.name}]"
          else {
            val lambdaParamNames = (0 until instanceArity).map(idx => s"tc$idx")

            val lambdaParams =
              instanceTypeParams.zip(lambdaParamNames).map {
                case (p, name) => renderTypeParam(p, Option(name))
              }

            val appliedTypeParams =
              nonOverlappingOwnerParams ++
                lambdaParams.takeRight(ownerArity - nonOverlappingOwnerParams.size)

            val appliedTypeParamsText =
              if (appliedTypeParams.isEmpty) ""
              else                           appliedTypeParams.commaSeparated(Model.SquareBrackets)

            s"${tc.qualifiedName}[[${lambdaParams.commaSeparated()}] =>> ${owner.name}$appliedTypeParamsText]"
          }

        val typeParametersText =
          if (nonOverlappingOwnerParams.isEmpty) ""
          else nonOverlappingOwnerParams.commaSeparated(Model.SquareBrackets)

        Option(s"given derived$$$refName$typeParametersText: $resultTypeText = $derivedFqn")
      } else if (instanceArity == 0 && ownerTypeParams.forall(isTypeKinded)) {
        // case (b)
        val typeParamInstancesText =
          ownerTypeParams.map(p => s"${tc.qualifiedName}[${p.name}]").mkString("(using ", ", ", ")")

        val typeParamString = ownerTypeParams.map(_.name).commaSeparated(Model.SquareBrackets)

        val resultTypeText = s"${tc.qualifiedName}[${owner.name}$typeParamString]"

        Option(s"given derived$$$refName$typeParamString$typeParamInstancesText: $resultTypeText = $derivedFqn")
      } else None
    }
  }

  /**
   * Produces text of a given definition of `CanEqual` typeclass instance.
   * Specific derivation rules for `CanEqual[L, R]` and ADT `Foo[A, B, C[_]]` are as follows:
   *  1) Type params of the deriving class correspond to all and only
   *     elements of the deriving class which are relevant to equality
   *  2) Type parameters of kind other than 'Type' are assumed to be irrelevant
   *     for derivation purposes.
   *
   *  Procedure: construct two column matrix of the deriving class type parameters
   *             and the CanEqual type class parameters:
   *             A_L A_R
   *             B_L B_R
   *             C_L C_R,
   *             retain only the pairs, which `CanEqual` can be applied to, i.e. of kind 'Type',
   *             require pairwise `CanEqual` instances,  `CanEqual[A_L, A_R]` and `CanEqual[B_L, B_R]`
   *             to produce `CanEqual[Foo[A_L, B_L, C_L], Foo[A_R, B_R, C_R]]`
   */
  def deriveCanEqual(owner: ScDerivesClauseOwner): String = {
    def prependSuffixToName(tps: Seq[TypeParameter], suffix: String): Seq[String] =
      tps.map(tp => s"${tp.name}_$suffix")

    val ownerTypeParams = owner.typeParameters.map(TypeParameter(_))
    val typeKindedTps   = ownerTypeParams.filter(isTypeKinded)

    val pairwiseInstances =
      if (typeKindedTps.isEmpty) ""
      else
        prependSuffixToName(typeKindedTps, "L")
          .zip(prependSuffixToName(typeKindedTps, "R"))
          .map { case (l, r) => s"CanEqual[$l, $r]" }.mkString("(using ", ", ", ")")

    val leftParams = prependSuffixToName(ownerTypeParams, "L")
    val rightParams = prependSuffixToName(ownerTypeParams, "R")
    val allParamsText = typeParamsString(leftParams ++ rightParams)

    s"""given derived$$CanEqual$allParamsText$pairwiseInstances:
       |CanEqual[${owner.name}${typeParamsString(leftParams)},
       |         ${owner.name}${typeParamsString(rightParams)}] = ???""".stripMargin
  }

  /**
   * Checks if typeclass `tc` can be used in a derives clause of `owner`,
   * if not returns an error message.
   */
  def checkIfCanBeDerived(tc: ScTypeDefinition, refName: String, owner: ScDerivesClauseOwner): Either[String, String] = {
    if (tc.qualifiedName == "scala.CanEqual") Right(deriveCanEqual(owner))
    else if (tc.typeParameters.isEmpty)
      Left(ScalaBundle.message("derives.type.has.no.type.parameters", tc.name))
    else if (tc.typeParameters.size > 1)
      Left(ScalaBundle.message("derives.cannot.be.unified", owner.name, tc.name))
    else {
      tc.baseCompanion match {
        case None =>
          Left(ScalaBundle.message("derives.type.has.no.companion.object", tc.name))
        case Some(companion) =>

          if (findDerivedMethods(companion, owner).isEmpty)
            Left(ScalaBundle.message("derives.no.member.named.derived", tc.name))
          else {
            DerivesUtil.deriveSingleParameterTypeClass(refName, tc, owner).toRight(
              ScalaBundle.message("derives.cannot.be.unified", owner.name, tc.name)
            )
          }
      }
    }
  }

  def findDerivedMethods(companion: ScTypeDefinition, place: PsiElement): Set[ScalaResolveResult] = {
    val processor = new MethodResolveProcessor(
      place,
      "derived",
      List.empty,
      Seq.empty,
      Seq.empty,
      isShapeResolve = false,
    )

    val companionType = companion.`type`().getOrAny

    processor.processType(companionType, place, ScalaResolveState.empty)
    val candidatesWithoutImplicits = processor.candidatesS

    if (candidatesWithoutImplicits.forall(!_.isApplicable())) {
      processor.resetPrecedence()

      ImplicitConversionResolveResult.processImplicitConversionsAndExtensions(
        Option(processor.refName),
        place,
        processor,
        companionType.toOption,
        noImplicitsForArgs = false,
        forCompletion = false
      )(identity)(place)

      processor.candidatesS
    } else candidatesWithoutImplicits
  }

  def resolveTypeClassReference(ref: ScReference): Either[String, ScTypeDefinition] =
    ref.bind().toRight(ScalaBundle.message("derives.scala.no.resolve")).flatMap {
      srr => srr.element match {
        case tc: ScClass => Right(tc)
        case tc: ScTrait => Right(tc)
        case _: PsiClass => Left(ScalaBundle.message("derives.scala.class.expected"))
        case alias: ScTypeAliasDefinition =>
          val aliasedType = alias.aliasedType.getOrAny
          aliasedType.extractClass match {
            case Some(tc: ScClass) => Right(tc)
            case Some(tc: ScTrait) => Right(tc)
            case Some(_)           => Left(ScalaBundle.message("derives.scala.class.expected"))
            case None =>
              Left(ScalaBundle.message("derives.not.a.class.type", aliasedType.presentableText(ref)))
          }
      }
    }

  private def typeParamsString(tps: Seq[String]): String =
    if (tps.isEmpty) ""
    else             tps.commaSeparated(Model.SquareBrackets)

  private def renderTypeParam(param: TypeParameter, name: Option[String] = None): String = {
    val typeParameters = param.typeParameters
    val typeParamsText = typeParamsString(typeParameters.map(renderTypeParam(_)))
    val paramName      = name.getOrElse(param.name)
    s"$paramName$typeParamsText"
  }

  private def isTypeKinded(typeParam: TypeParameter): Boolean =
    typeParam.typeParameters.isEmpty
}
