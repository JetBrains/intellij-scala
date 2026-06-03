package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{Model, ObjectExt, StringsExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScDerivesClauseOwner, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext, TypeVariableUnification}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}

object DerivesUtil {

  private val LOG: Logger = Logger.getInstance(getClass)

  /**
   * Check if ADT can be unified with typeclass' type parameter and produce text of a given definition,
   * which should be put into ADT's companion object.
   * Derivation mechanism covers two cases (a) and (b):<br>
   *  1. (a) ADT and type class parameters overlap on the right and have the same kinds at the overlap.
   *     {{{
   *         trait TC[F[X, Y]]
   *         case class Foo1[A, B, C] derives TC
   *         // given derived$TC[A]: TC[[x, y] =>> Foo1[A, x, y]]
   *
   *         case class Foo2[A, B] derives TC
   *         // natural case, given derived$TC: TC[Foo2]
   *
   *         case class Foo3[A] derives TC
   *         // given derived$TC: TC[[x, y] =>> Foo3[y]]
   *
   *         case class Foo4 derives TC
   *         // given derived$TC: TC[[x, y] =>> Foo4]
   *     }}}
   *  1. (b) The type class type parameter and all ADT type parameters are of kind 'Type'
   *     <p>
   *     In this case, the ADT has at least one type parameter of kind 'Type';
   *     otherwise it would already have been covered as a "natural" case for a type class of the form F[_].
   *     <p>
   *     The derived instance has a type parameter and a given for
   *     each of the type parameters of the ADT. {{{
   *         trait TC[T]
   *         case class C[A, B, C] derives TC
   *         //given derived$TC[A, B, C](given TC[A], TC[B], TC[C]): TC[C[A, B, C]]
   *     }}}
   *
   * @param derivesReferenceText typeclass reference text as written in the "derives" clause;
   *                             we preserve this original text for synthetic signature generation because resolve happens
   *                             in the deriving type's lexical scope where local aliases/imports and fully qualified forms matter.
   *                             Replacing it with `tc.name` can break source-level resolution in cases like
   *                             `derives CaseClassAliasName` or `derives _root_.foo.bar.Functor`.
   * @param tc resolved typeclass definition for the derives reference.
   * @param derivingType class, trait, or enum for which synthetic given text is generated.
   * @param useRealDerivedRhs `true` to generate the real `<typeclass>.derived` RHS;
   *                          `false` to generate a stub RHS for resolve/type inference.
   */
  private def deriveSingleParameterTypeClass(
    derivesReferenceText: String,
    tc                  : ScTypeDefinition,
    derivingType        : ScDerivesClauseOwner,
    useRealDerivedRhs   : Boolean
  ): Option[String] = {
    if (tc.typeParameters.size != 1) None
    else {
      val derivedRhs: String = if (useRealDerivedRhs)
        s"$derivesReferenceText.derived"
      else
        "_root_.scala.Predef.???"

      // Source deriving types should preserve original reference text from the "derives" clause to keep local/path-dependent
      // references resolvable (`class Test { trait TC[A]; case class C() derives TC }`).
      // Compiled deriving types may no longer have source imports/aliases, so we prefer fully qualified text
      val typeClassReferenceTextOrQualifiedName: String =
        if (derivingType.isInCompiledFile) {
          Option(tc.qualifiedName).getOrElse {
            LOG.error(
              s"Unexpected empty qualified name for a decompiled typeclass during derives synthesis: " +
                s"typeClass=${tc.name}, derivesReferenceText=$derivesReferenceText, derivingType=${Option(derivingType.qualifiedName).getOrElse(derivingType.name)}"
            )
            derivesReferenceText
          }
        } else
          derivesReferenceText

      // Keep names stable across aliases in the "derives" clause:
      // `derives MyFunctor` where `MyFunctor` aliases `cats.Functor` still generates `derived$Functor`.
      // This can theoretically collide for distinct type classes with the same short name,
      // but in practice Scala reports duplicate derivation and does not generate both instances.
      val typeClassMemberName       = tc.name
      val typeClassParamType        = TypeParameter(tc.typeParameters.head)
      val instanceTypeParams        = typeClassParamType.typeParameters
      val instanceArity             = instanceTypeParams.size
      val derivingTypeParams        = derivingType.typeParameters.map(TypeParameter(_))
      val derivingTypeArity         = derivingTypeParams.size
      val alignedDerivingTypeParams = derivingTypeParams.takeRight(instanceArity)
      val alignedInstanceTypeParams = instanceTypeParams.takeRight(alignedDerivingTypeParams.length)

      if ((instanceArity > 0 || instanceArity == derivingTypeArity) &&
        TypeVariableUnification.unifiableKinds(alignedDerivingTypeParams, alignedInstanceTypeParams)) {
        // case (a)
        val nonOverlappingDerivingTypeParams =
          derivingTypeParams.dropRight(instanceArity).map(_.name)

        val resultTypeText: String =
          if (instanceArity == derivingTypeArity)
            s"$typeClassReferenceTextOrQualifiedName[${derivingType.name}]"
          else {
            val lambdaParamNames = (0 until instanceArity).map(idx => s"tc$idx")

            val lambdaParams =
              instanceTypeParams.zip(lambdaParamNames).map {
                case (p, name) => renderTypeParam(p, Option(name))
              }

            val appliedTypeParams =
              nonOverlappingDerivingTypeParams ++
                lambdaParams.takeRight(derivingTypeArity - nonOverlappingDerivingTypeParams.size)

            val appliedTypeParamsText =
              if (appliedTypeParams.isEmpty) ""
              else                           appliedTypeParams.commaSeparated(Model.SquareBrackets)

            s"$typeClassReferenceTextOrQualifiedName[[${lambdaParams.commaSeparated()}] =>> ${derivingType.name}$appliedTypeParamsText]"
          }

        val typeParametersText =
          if (nonOverlappingDerivingTypeParams.isEmpty) ""
          else nonOverlappingDerivingTypeParams.commaSeparated(Model.SquareBrackets)

        Option(s"given derived$$$typeClassMemberName$typeParametersText: $resultTypeText = $derivedRhs")
      } else if (instanceArity == 0 && derivingTypeParams.forall(isTypeKinded)) {
        // Example: "[A, B]"
        val typeParametersText =
          derivingTypeParams.map(_.name).commaSeparated(Model.SquareBrackets)

        // case (b)
        // Example: "(using _root_.example.Functor[A], _root_.example.Functor[B])"
        val usingEvidenceText =
          derivingTypeParams.map(p => s"$typeClassReferenceTextOrQualifiedName[${p.name}]").mkString("(using ", ", ", ")")

        // Example: "_root_.example.Functor[Box1[A, B]]"
        val resultTypeText =
          s"$typeClassReferenceTextOrQualifiedName[${derivingType.name}$typeParametersText]"

        // Example:
        // given derived$Functor[A, B](using _root_.example.Functor[A], _root_.example.Functor[B]):
        //   _root_.example.Functor[Box1[A, B]] = _root_.example.Functor.derived
        Option(s"given derived$$$typeClassMemberName$typeParametersText$usingEvidenceText: $resultTypeText = $derivedRhs")
      } else {
        None
      }
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
  def deriveCanEqual(derivingType: ScDerivesClauseOwner): String = {
    def prependSuffixToName(tps: Seq[TypeParameter], suffix: String): Seq[String] =
      tps.map(tp => s"${tp.name}_$suffix")

    val derivingTypeParams = derivingType.typeParameters.map(TypeParameter(_))
    val typeKindedTps      = derivingTypeParams.filter(isTypeKinded)

    val pairwiseInstances =
      if (typeKindedTps.isEmpty) ""
      else
        prependSuffixToName(typeKindedTps, "L")
          .zip(prependSuffixToName(typeKindedTps, "R"))
          .map { case (l, r) => s"CanEqual[$l, $r]" }.mkString("(using ", ", ", ")")

    val leftParams = prependSuffixToName(derivingTypeParams, "L")
    val rightParams = prependSuffixToName(derivingTypeParams, "R")
    val allParamsText = typeParamsString(leftParams ++ rightParams)

    s"""given derived$$CanEqual$allParamsText$pairwiseInstances: CanEqual[
       |  ${derivingType.name}${typeParamsString(leftParams)},
       |  ${derivingType.name}${typeParamsString(rightParams)}
       |] = ???""".stripMargin
  }

  /**
   * Checks whether `typeClassRef` can be used in a `derives` clause of `derivingType` and, when it can,
   * produces the corresponding synthetic given definition text.
   *
   * This method first resolves the type class reference. Then it validates the part needed to build the synthetic given
   * LHS: the type class must have a supported type-parameter shape, and its type parameter must be unifiable with the
   * deriving type according to Scala 3 derivation rules.
   *
   * The `useRealDerivedRhs` flag controls whether the generated synthetic member uses
   * the actual RHS (`<typeclass>.derived`) or a stub RHS.
   *
   * @param typeClassRef       type class reference as written in the source `derives` clause.
   * @param derivingType       class, trait, or enum that owns the `derives` clause and receives the synthetic given.
   * @param shouldValidateDerivedMethod  `true` when directly annotating a `derives` clause.
   *                           In this mode the generated synthetic member keeps the real
   *                           `<typeclass>.derived` RHS, and this method also checks that the
   *                           companion and the `derived` member exist, so primary derives errors
   *                           can be reported at the clause.
   *                           `false` when generating members for resolve/type inference.
   *                           In this mode the generated given uses `???` as a stub RHS and avoids
   *                           resolving/type-checking `.derived`, so invalid RHS code does not create
   *                           secondary usage-site errors when the synthetic LHS is valid.
   * @return `Right(syntheticGivenText)` when a synthetic given can be formed.
   *         When `useRealDerivedRhs` is `false`, the returned text uses the stub RHS `???`;
   *         when it is `true`, it uses the real `<typeclass>.derived` RHS.<br>
   *         `Left(errorMessage)` when the type class reference cannot be resolved, when the type class cannot be used
   *         for this derives clause, or, in highlighting mode only, when the derives RHS cannot be validated because
   *         the companion object or `derived` member is missing.
   * @example synthetic-member mode, where only the LHS is needed:
   *          {{{
   *          trait Eq[A]
   *          object Eq { def derived[A]: Eq[A] = ??? }
   *          case class Foo() derives Eq
   *          // Right("given derived$Eq: Eq[Foo] = _root_.scala.Predef.???")
   *          }}}
   *          Parameter mapping in this example:
   *           - `typeClassRef` is the `Eq` reference inside `derives Eq`<br>
   *             `typeClass` is the internally resolved `trait Eq[A]`.
   *           - `derivesReferenceText` is `typeClassRef.getText`, namely `"Eq"`.
   *           - `derivingType` is the `case class Foo() derives Eq` PSI element.
   *           - `useRealDerivedRhs = false` is synthetic-member mode, so the RHS is the stub `???`
   * @example derives-clause highlighting mode, where the RHS must be valid:
   *          {{{
   *          trait Eq[A]
   *          object Eq
   *          case class Foo() derives Eq
   *
   *          checkIfCanBeDerived(
   *            typeClassRef = eqDerivesReference,
   *            derivingType = fooClass,
   *            useRealDerivedRhs = true
   *          )
   *          // Left("Value derived is not a member of object Eq")
   *          }}}
   */
  def synthesizeDerivedGiven(
    typeClassRef: ScReference,
    derivingType: ScDerivesClauseOwner,
    shouldValidateDerivedMethod: Boolean
  ): Either[String, String] = {
    val typeClassResolved = resolveTypeClassReference(typeClassRef)
    typeClassResolved.flatMap { typeClass =>
      synthesizeDerivedGiven(
        typeClass = typeClass,
        derivesReferenceText = typeClassRef.getText,
        derivingType = derivingType,
        shouldValidateDerivedMethod = shouldValidateDerivedMethod
      )
    }
  }

  private def synthesizeDerivedGiven(
    typeClass: ScTypeDefinition,
    derivesReferenceText: String,
    derivingType: ScDerivesClauseOwner,
    shouldValidateDerivedMethod: Boolean
  ): Either[String, String] = {
    if (typeClass.qualifiedName == "scala.CanEqual")
      Right(deriveCanEqual(derivingType))
    else if (typeClass.typeParameters.isEmpty)
      Left(ScalaBundle.message("derives.type.has.no.type.parameters", typeClass.name))
    else if (typeClass.typeParameters.size > 1)
      Left(ScalaBundle.message("derives.cannot.be.unified", derivingType.name, typeClass.name))
    else {
      val rhsValidationError: Option[String] =
        if (shouldValidateDerivedMethod)
          validateTypeClassCompanionHasDerivedMethod(typeClass, derivingType)
        else
          None

      rhsValidationError match {
        case Some(error) =>
          Left(error)
        case None =>
          val maybeString = DerivesUtil.deriveSingleParameterTypeClass(
            derivesReferenceText,
            typeClass,
            derivingType,
            useRealDerivedRhs = shouldValidateDerivedMethod
          )
          maybeString.toRight(ScalaBundle.message("derives.cannot.be.unified", derivingType.name, typeClass.name))
      }
    }
  }

  /**
   * The validation effectively takes place only during highlighting of `derives` in the annotator.
   * For the type inference we don't run the validation
   */
  private def validateTypeClassCompanionHasDerivedMethod(
    typeClass: ScTypeDefinition,
    derivingType: ScDerivesClauseOwner
  ): Option[String] = {
    val typeClassCompanionObject = typeClass.baseCompanion.orElse(typeClass.fakeCompanionModule)
    typeClassCompanionObject match {
      case Some(companion: ScObject) =>
        val derivesMethodResult = findDerivedMethods(companion, derivingType)
        if (derivesMethodResult.nonEmpty)
          None
        else
          Some(ScalaBundle.message("derives.no.member.named.derived", typeClass.name))
      case _ =>
        Some(ScalaBundle.message("derives.type.has.no.companion.object", typeClass.name))
    }
  }

  def findDerivedMethods(companion: ScTypeDefinition, place: PsiElement): Set[ScalaResolveResult] = {
    val processor = new MethodResolveProcessor(
      place,
      "derived",
      List.empty,
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

  def resolveTypeClassReference(ref: ScReference): Either[String, ScTypeDefinition] = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(ref)
    implicit val context: Context = Context(ref)

    ref.bind().toRight(ScalaBundle.message("derives.scala.no.resolve")).flatMap {
      srr => srr.element match {
        case tc: ScClass => Right(tc)
        case tc: ScTrait => Right(tc)
        case _: PsiClass => Left(ScalaBundle.message("derives.scala.class.expected"))
        case alias: ScTypeAliasDefinition if !alias.isEffectivelyOpaque =>
          val aliasedType = alias.aliasedType.getOrAny
          aliasedType.extractClass match {
            case Some(tc: ScClass) => Right(tc)
            case Some(tc: ScTrait) => Right(tc)
            case Some(_)           => Left(ScalaBundle.message("derives.scala.class.expected"))
            case None =>
              Left(ScalaBundle.message("derives.not.a.class.type", aliasedType.presentableText))
          }
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
