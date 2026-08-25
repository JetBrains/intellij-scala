package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.util.SlowOperations
import org.jetbrains.plugins.scala.caches.measure
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{ImplicitArgumentsClause, SafeCheckException}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGiven, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.ExtensionConversionHelper.extensionConversionCheck
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.{isUnderspecified, _}
import org.jetbrains.plugins.scala.lang.psi.light.LightContextFunctionParameter
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{MethodResolveProcessor, MostSpecificUtil}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.tailrec
import scala.collection.{View, mutable}
import scala.util.Using

object ImplicitCollector {
  def cache(project: Project): ImplicitCollectorCache =
    ScalaPsiManager.instance(project).implicitCollectorCache

  sealed trait ImplicitResult

  sealed trait FullInfoResult extends ImplicitResult
  case object NoResult        extends ImplicitResult

  case object OkResult                        extends FullInfoResult
  case object ImplicitParameterNotFoundResult extends FullInfoResult
  case object DivergedImplicitResult          extends FullInfoResult
  case object CantInferTypeParameterResult    extends FullInfoResult

  case object TypeDoesntConformResult       extends ImplicitResult
  case object BadTypeResult                 extends ImplicitResult
  case object CantFindExtensionMethodResult extends ImplicitResult
  case object UnhandledResult               extends ImplicitResult
  case object FunctionForParameterResult    extends ImplicitResult

  case class ImplicitState(
    place:                      PsiElement,
    tp:                         ScType,
    expandedTp:                 ScType,
    coreElement:                Option[ScNamedElement],
    isImplicitConversion:       Boolean,
    recursionDepth:             Int,
    extensionData:              Option[ExtensionConversionData],
    fullInfo:                   Boolean,
    previousDivergenceStack:    Option[DivergenceChecker.DivergenceStack]
  ) {
    lazy val tooUnspecificToSearch: Boolean =
      ImplicitCollector.isUnderspecified(isImplicitConversion, extensionData.isDefined, place, tp)

    def presentableTypeText: String = presentableText(tp)

    /**
     * Presentation of the type the search was done for. An inference variable is presented by the
     * bound it was constrained to (`Int` for `val i: Int = summon`), the way the compiler reports it,
     * rather than as an abstract type (`T_`).
     */
    def presentableSearchedTypeText: String = {
      implicit val context: Context = Context(place)

      tp match {
        case abstractType: ScAbstractType => presentableText(abstractType.simplifyType)
        case _                            => presentableTypeText
      }
    }

    private def presentableText(tpe: ScType): String = {
      implicit val tpc: TypePresentationContext = TypePresentationContext(place)
      implicit val context: Context = Context(place)

      Using.resource(SlowOperations.knownIssue("SCL-23054"))(_ => tpe.presentableText)
    }
  }

  def probableArgumentsFor(parameter: ScalaResolveResult): Seq[(ScalaResolveResult, FullInfoResult)] = {
    parameter.implicitSearchState.map { state =>
      val collector = new ImplicitCollector(state.copy(fullInfo = true))

      collector.collect().flatMap { r =>
        r.implicitReason match {
          case CantInferTypeParameterResult => Seq.empty
          case reason: FullInfoResult       => Seq((r, reason))
          case _                            => Seq.empty
        }
      }
    }.getOrElse(Seq.empty)
  }

  //@TODO: inspect usages outside of ImplicitCollector and adapt to visibleImplicitsByLevel if needed.
  def visibleImplicits(place: PsiElement): Set[ScalaResolveResult] =
    ImplicitSearchScope.forElement(place).cachedVisibleImplicits

  def visibleImplicitsByLevel(place: PsiElement): collection.Seq[collection.Set[ScalaResolveResult]] =
    ImplicitSearchScope.forElement(place).cachedVisibleImplicitsByLevel

  def implicitsFromType(
    place:                  PsiElement,
    scType:                 ScType,
  ): Set[ScalaResolveResult] =
    ImplicitSearchScope.forElement(place).cachedImplicitsByType(scType)

  def isValidImplicitResult(srr: ScalaResolveResult): Boolean =
    !srr.problems.contains(WrongTypeParameterInferred) && srr.implicitReason != TypeDoesntConformResult

  /** Whether the implicit search for `parameter` was refused, see [[ImplicitCollector.tooUnspecificToSearch]]. */
  def isTooUnspecificToSearch(parameter: ScalaResolveResult): Boolean =
    parameter.implicitSearchState.exists(_.tooUnspecificToSearch)

  /**
   * Presentation of `parameter`'s expected type for error messages. For an underspecified inference
   * variable this is the bare type parameter name (`M`), like the compiler reports it, rather than
   * the presentation of the abstract type (`M_`).
   */
  def expectedTypeText(parameter: ScalaResolveResult): Option[String] =
    parameter.implicitSearchState.map { state =>
      state.tp match {
        case ScAbstractType(typeParameter, _, _) if isUnderspecified(state.tp) => typeParameter.name
        case _                                                                 => state.presentableSearchedTypeText
      }
    }

  /**
   * Mirrors `Implicits.isUnderspecified` in the Scala 3 compiler: no implicit search is attempted
   * if the expected type is not specific enough, i.e. an inference variable whose only useful
   * information is an `Any`/`AnyRef` upper bound (`def f[M](using M)`, `def f[A, M >: Box[A]](using M)`).
   * Searching anyway would match an *arbitrary* implicit in scope (SCL-23860, e.g. `Predef.$conforms`),
   * whereas the compiler reports "No implicit search was attempted ... not specific enough".
   * Implicit conversions are exempt: dotc handles them with a different rule (`ViewProto`).
   */
  private def isUnderspecified(isImplicitConversion: Boolean,
                               isExtensionConversion: Boolean,
                               place: PsiElement,
                               tp: ScType): Boolean =
    !isImplicitConversion && !isExtensionConversion &&
      place.isInScala3File && isUnderspecified(tp)


  /** Mirrors `dotty.tools.dotc.typer.Implicits#isUnderspecified` for implicit parameter types. */
  @tailrec
  private def isUnderspecified(tpe: ScType): Boolean = tpe match {
    case ScAbstractType(_, _, upper) => isUnderspecified(upper)
    case tp                          => tp.isAny || tp.isAnyRef
  }
}

/**
 * @param place                   The call site
 * @param tp                      Search for an implicit definition of this type. May have type variables.
 * @param forDeferredGivenInClass Template definition for which deferred given instance search was initiated.
 *                                In this case, a special kind of lexical scope is used, even though `place` is technically
 *                                inside the template definition, only constructor parameters contribute to it.
 * @param withExtensions          If true, include Scala 3 extension methods.
 */
class ImplicitCollector(
  place:                      PsiElement,
  tp:                         ScType,
  expandedTp:                 ScType,
  coreElement:                Option[ScNamedElement],
  isImplicitConversion:       Boolean,
  recursionDepth:             Int                                       = 0,
  extensionData:              Option[ExtensionConversionData]           = None,
  fullInfo:                   Boolean                                   = false,
  previousDivergenceStack:    Option[DivergenceChecker.DivergenceStack] = None,
  withExtensions:             Boolean                                   = false,
  forCompletion:              Boolean                                   = false,
  forDeferredGivenInClass:    Option[ScTemplateDefinition]              = None
) {
  def this(state: ImplicitState) =
    this(
      state.place,
      state.tp,
      state.expandedTp,
      state.coreElement,
      state.isImplicitConversion,
      state.recursionDepth,
      state.extensionData,
      state.fullInfo,
      state.previousDivergenceStack
    )

  lazy val collectorState: ImplicitState =
    ImplicitState(
      place,
      tp,
      expandedTp,
      coreElement,
      isImplicitConversion,
      recursionDepth,
      extensionData,
      fullInfo,
      Option(DivergenceChecker.currentStack)
    )

  private val project = place.getProject
  private implicit def projectContext: ProjectContext = project
  private implicit def context: Context = Context(place)

  private val targetClass: Option[PsiClass]         = tp.extractClass
  private lazy val targetFunctionArity: Option[Int] = targetClass.flatMap(extractTargetFunctionArity)

  private val mostSpecificUtil: MostSpecificUtil = MostSpecificUtil(place, 1)

  private def isExtensionConversion: Boolean = extensionData.isDefined

  lazy val tooUnspecificToSearch: Boolean =
    ImplicitCollector.isUnderspecified(isImplicitConversion, isExtensionConversion, place, tp)

  /**
   * Returns `true` when a candidate may expose target extension methods via its resulting type.
   *
   * We use it in two places:
   * 1) during candidate collection, to avoid dropping extension carriers too early;
   * 2) in the second implicit-search pass (`withLocalTypeInference = true`), where we usually skip non-generic
   *    callables because pass 1 already handled them. Extension carriers are the exception: pass 2 can still be
   *    required to instantiate extension method type params against a concrete call site.
   *
   * Typical case: a given (explicit or derives-generated) returns `Functor[Bar]`, and `Functor` defines
   * `extension [A](fa: F[A]) def mapExt[B](f: A => B): F[B]`.
   * Even if the given itself is non-generic at the outer level, we still need it in pass 2 so `mapExt` is extracted
   * and locally instantiated for calls like `Bar(42).mapExt(_ + 1)`.
   *
   * If return-type extraction is inconclusive, we conservatively return `true` to avoid false negatives.
   */
  private def canContainTargetMethod(srr: ScalaResolveResult): Boolean = measure("ImplicitCollector.canContainTargetMethod") {
    withExtensions && !srr.isExtensionCall && !hasExplicitClause(srr) && {
      val targetType = srr.element match {
        case param: ScParameter => param.insideParamType.toOption
        case fun: ScFunction    => fun.returnType.toOption
        case _                  => None
      }

      val targetName = extensionData.map(_.refName)

      val hasTargetMethod =
        for {
          rtpe <- targetType
          cls  <- rtpe.extractClass
          tdef <- cls.asOptionOf[ScTypeDefinition]
        } yield {
          val methods =
            targetName match {
              case Some(name) => tdef.methodsByName(name)
              case None       => tdef.allMethods
            }

          methods.exists(_.isExtensionMethod)
        }

      hasTargetMethod.getOrElse(true)
    }
  }

  private def doImplicitSearch(): Seq[ScalaResolveResult] = {
    import scala.collection.{Seq, Set}
    //Step 1: Process only extension candidates in lexical scope
    //Step 2: Try implicits/givens from lexical scope and extensions inside given definitions
    //Step 3: Try implicits/givens/extension from implicit scope and extension inside given definitions
    val classParametersForDeferredGiven =
      forDeferredGivenInClass.collect {
        case cls: ScClass =>
          cls.parameters.view.collect {
            case p if p.isImplicit => new ScalaResolveResult(p)
          }.to(Set)
      }.getOrElse(Set.empty)

    val lexicalScopeCandidates =
      if (place.isInScala3File)
        visibleNamesCandidatesByLevel() :+ classParametersForDeferredGiven
      else
        Seq(visibleNamesCandidates() ++ classParametersForDeferredGiven)

    @tailrec
    def collectCompatibleCandidatesFromLexicalScope(
      setsIterator:   Iterator[Set[ScalaResolveResult]],
      extensionsOnly: Boolean
    ): scala.Seq[ScalaResolveResult] =
      if (setsIterator.isEmpty) scala.Seq.empty
      else {
        val levelSet                                    = setsIterator.next()
        val (visibleExtensions, otherVisibleCandidates) = levelSet.partition(_.isExtensionCall)

        val extensionCandidates =
          if (withExtensions && extensionsOnly) collectCompatibleExtensionCandidates(visibleExtensions)
          else                                  scala.Seq.empty

        if (extensionCandidates.exists(_.isApplicable())) extensionCandidates
        else if (!extensionsOnly) {
          val firstCandidates = collectCompatibleCandidates(otherVisibleCandidates)

          if (firstCandidates.exists(_.isApplicable())) firstCandidates
          else
            collectCompatibleCandidatesFromLexicalScope(setsIterator, extensionsOnly)
        } else collectCompatibleCandidatesFromLexicalScope(setsIterator, extensionsOnly)
      }

    // Step 1: only extensions from lexical scope
    val applicableVisibleExtensions =
      collectCompatibleCandidatesFromLexicalScope(lexicalScopeCandidates.iterator, extensionsOnly = true)

    // If we find exactly one extension method, that extension will be chosen regardless of application errors
    // and other implicit conversions, so we can stop searching further and just return that one extension
    if (applicableVisibleExtensions.sizeIs == 1) applicableVisibleExtensions
    else {
      //Step 2: other candidates from lexical scope
      val applicableVisibleCandidates =
        applicableVisibleExtensions ++
          collectCompatibleCandidatesFromLexicalScope(lexicalScopeCandidates.iterator, extensionsOnly = false)

      if (applicableVisibleCandidates.nonEmpty) applicableVisibleCandidates
      else
        collectCompatibleCandidates(fromTypeCandidates())
    }
  }

  def collect(): Seq[ScalaResolveResult] = {
    if (tooUnspecificToSearch) return Seq.empty

    DivergenceChecker.withDivergenceStackOpt(previousDivergenceStack) {
      ProgressManager.checkCanceled()
      if (fullInfo) {
        //@TODO: should this branch also uses visibleNamesCandidatesByLevel?
        val visible            = visibleNamesCandidates()
        val fromNameCandidates = collectFullInfo(visible)

        val allCandidates =
          if (fromNameCandidates.exists(_.implicitReason == OkResult)) fromNameCandidates
          else {
            val fromTypeNotVisible =
              fromTypeCandidates()
                .filterNot(c => visible.exists(_.element == c.element))

            fromNameCandidates ++ collectFullInfo(fromTypeNotVisible)
          }

        //todo: should we also compare types like in MostSpecificUtil.isAsSpecificAs ?
        allCandidates.sortWith(mostSpecificUtil.isInMoreSpecificClass)
      } else if (forCompletion) {
        val allCandidates = visibleNamesCandidates() ++ fromTypeCandidates()
        collectCompatibleForCompletion(allCandidates)
      } else {
        ImplicitCollector.cache(project)
          .getOrCompute(place, tp, mayCacheResult = !isExtensionConversion)(
            doImplicitSearch()
          )
      }
    }
  }

  private def visibleNamesCandidates(): Set[ScalaResolveResult] =
    ImplicitCollector.visibleImplicits(place)
      .map(_.copy(implicitSearchState = Option(collectorState)))

  private def visibleNamesCandidatesByLevel() =
    ImplicitCollector.visibleImplicitsByLevel(place)
      .map(_.map(_.copy(implicitSearchState = Option(collectorState))))

  private def fromTypeCandidates(): Set[ScalaResolveResult] =
    ImplicitCollector.implicitsFromType(place, expandedTp)
      .map(_.copy(implicitSearchState = Option(collectorState)))

  private def collectCompatibleCandidates(candidates: collection.Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    //implicits found without local type inference have higher priority
    val withoutLocalTypeInference = collectCompatibleCandidates(candidates, withLocalTypeInference = false)

    val compatible =
      if (withoutLocalTypeInference.nonEmpty) withoutLocalTypeInference
      else                                    collectCompatibleCandidates(candidates, withLocalTypeInference = true)

    if (compatible.forall(_.isExtensionCall)) compatible.toSeq
    else
      mostSpecificUtil.mostSpecificForImplicitParameters(compatible) match {
        case Some(r) => Seq(r)
        case _       => compatible.toSeq
      }
  }

  private def collectCompatibleExtensionCandidates(candidates: collection.Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    def collect(allowReceiverConversion: Boolean): Seq[ScalaResolveResult] = {
      val withoutLocalTypeInference =
        collectCompatibleCandidates(
          candidates,
          withLocalTypeInference             = false,
          allowExtensionReceiverConversions = allowReceiverConversion
        ).toSeq

      // If there is exactly one extension method, that one will be chosen regardless of application errors.
      if (withoutLocalTypeInference.sizeIs == 1)
        withoutLocalTypeInference
      else
        withoutLocalTypeInference ++ collectCompatibleCandidates(
          candidates,
          withLocalTypeInference             = true,
          allowExtensionReceiverConversions = allowReceiverConversion
        )
    }

    // Scalac resolves the synthesized extension receiver without views first and retries with
    // implicit conversions only when that phase is empty. Mixing both phases makes a directly
    // applicable receiver ambiguous with, or even less specific than, a converted receiver.
    val withoutReceiverConversions = collect(allowReceiverConversion = false)
    if (withoutReceiverConversions.nonEmpty)
      withoutReceiverConversions
    else
      collect(allowReceiverConversion = true)
  }


  private def collectFullInfo(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    val allCandidates =
      candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = false)) ++
        candidates.flatMap(c => checkCompatible(c, withLocalTypeInference = true))

    val afterExtensionPredicate = allCandidates.flatMap(applyExtensionPredicate)

    afterExtensionPredicate
      .filter(_.implicitReason.is[FullInfoResult])
      .toSeq
  }

  private def collectCompatibleForCompletion(candidates: Set[ScalaResolveResult]): Seq[ScalaResolveResult] = {
    val filteredCandidates = mutable.HashSet.empty[ScalaResolveResult]

    for (c <- candidates) {
      val compatible = checkCompatible(c, withLocalTypeInference = false) ++ checkCompatible(c, withLocalTypeInference = true)
      filteredCandidates ++= compatible.filter(isValidImplicitResult)
      if (withExtensions) {
        filteredCandidates ++= collectExtensionsFromImplicitResult(c, extensionData)
      }
    }
    filteredCandidates.toSeq
  }

  private def extractTargetFunctionArity(cls: PsiClass): Option[Int] =
    cls.qualifiedName match {
      case "java.lang.Object" => Some(-1)
      case name =>
        val arity = name.stripPrefix(FunctionType.TypeName)

        if (arity.nonEmpty && arity.forall(_.isDigit)) Option(arity.toInt)
        else                                           None
    }

  /**
   * Checks one implicit-search candidate against this collector's expected type [[tp]] at [[place]].
   * The result can carry an inferred substitutor, applied context arguments, or a diagnostic
   * [[ImplicitResult]] used while looking for extensions inside an otherwise incompatible carrier.
   *
   * A non-generic given is checked in the first pass without local type inference:
   * {{{
   *   given Ordering[Int] = ???
   *   summon[Ordering[Int]]
   * }}}
   *
   * A generic candidate is deferred until the local-inference pass, where `A` can be inferred as
   * `Int` from the requested `Ordering[List[Int]]`:
   * {{{
   *   given listOrdering[A](using Ordering[A]): Ordering[List[A]] = ???
   *   summon[Ordering[List[Int]]]
   * }}}
   *
   * For an extension receiver, the conversion-enabled pass may also check whether the qualifier can
   * be adapted to the declared receiver type:
   * {{{
   *   given Conversion[Source, Target] = ???
   *   extension (target: Target) def value: Int = 1
   *   (new Source).value
   * }}}
   * Passing `allowExtensionReceiverConversions = false` rejects that candidate in the direct
   * receiver phase; the conversion-enabled fallback accepts it only when no direct receiver applies.
   *
   * @param c candidate found in lexical or implicit scope; it can be a regular given/implicit,
   *          an extension method, or a value whose result type can provide extensions.
   * @param withLocalTypeInference whether unresolved candidate type parameters may be inferred from
   *                               the current search target. Generic functions are deferred when this
   *                               is `false`.
   * @param checkFast whether to perform only the preliminary compatibility check used to filter the
   *                  candidate set, skipping expensive context-argument application where possible.
   * @param allowExtensionReceiverConversions whether a nonconforming extension receiver may be made
   *                                          applicable through an implicit conversion. This has no
   *                                          effect on non-extension candidates.
   * @return the compatible (possibly adapted) resolve result, or `None` when the candidate cannot
   *         participate in this search.
   */
  def checkCompatible(
    c:                                 ScalaResolveResult,
    withLocalTypeInference:            Boolean,
    checkFast:                         Boolean = false,
    allowExtensionReceiverConversions: Boolean = true,
  ): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()

    c.element match {
      case fun: ScFunction =>
        val exportedInExtension = c.exportedInExtension
        val typeParams = fun.typeParametersWithExtension(exportedInExtension)

        //Discard extension candidates if we are searching for implicit parameters/implicit conversions for args
        if (!forCompletion && c.isExtensionCall && extensionData.forall(_.refName.isEmpty))
          return None

        // Pass 2 (`withLocalTypeInference = true`) normally skips non-generic callables because pass 1 already checked them.
        // Keep only extension carriers here: pass 2 may still be needed to extract/instantiate generic extension members
        // from their return type (for example, givens or derives-generated givens returning a typeclass with extensions).
        val mustAlwaysRunLocalTypeInference = typeParams.nonEmpty || canContainTargetMethod(c)
        if (withLocalTypeInference && !mustAlwaysRunLocalTypeInference) {
          return None
        }

        //scala.Predef.$conforms should be excluded
        if (isImplicitConversion && isPredefConforms(fun))
          return None

        val clauses = fun.effectiveParameterClauses
        //to avoid checking implicit functions in case of simple implicit parameter search
        val hasNonImplicitClause = clauses.exists(!_.isImplicit)
        if (!c.isExtensionCall && hasNonImplicitClause) {
          val clause      = clauses.head
          val paramsCount = clause.parameters.size

          if (!targetFunctionArity.exists(x => x == -1 || x == paramsCount)) {
            return reportWrong(c, FunctionForParameterResult, Seq(WrongTypeParameterInferred))
          }
        }

        checkFunctionTypeConformance(
          c,
          withLocalTypeInference,
          checkFast,
          typeParams,
          allowExtensionReceiverConversions
        )
      case _ =>
        if (withLocalTypeInference) {
          //only functions may have local type inference
          if (withExtensions)
            Option(c.copy(implicitReason = TypeDoesntConformResult))
          else
            None
        } else {
          simpleConformanceCheck(c)
        }
    }
  }

  /**
   * Extension methods need receiver-specificity selection before their receiver constraints are
   * folded into the result substitutor. By the time [[MethodResolveProcessor]] performs ordinary
   * overload resolution, a qualified extension has already been applied to its receiver, so its
   * remaining clauses no longer contain the information that distinguishes receiver overloads.
   *
   * For example, both declarations below reach later overload processing as `result: String`, even
   * though the second receiver is more specific for `List[Int]`:
   * {{{
   *   extension [A](value: Iterable[A]) def result: String = "iterable"
   *   extension [A](value: List[A]) def result: String = "list"
   *
   *   List(1).result // must select the List receiver
   * }}}
   * Grouping by name and selecting here compares the complete extension types, including their
   * receiver clauses, before qualifier application folds those constraints into the substitutor.
   */
  private def pickMostSpecificExtensions(
    candidates: mutable.HashSet[ScalaResolveResult]
  ): mutable.HashSet[ScalaResolveResult] =
    if (withExtensions)
      pickMostSpecificExtensionsByName(candidates)
    else
      candidates

  private def pickMostSpecificExtensionsByName(
    candidates: mutable.HashSet[ScalaResolveResult]
  ): mutable.HashSet[ScalaResolveResult] = {
    val candidatesByName = candidates.view.groupBy(_.name)
    candidatesByName
      .flatMap {
        case (_, sameNameCandidates) =>
          if (sameNameCandidates.forall(_.isExtensionCall)) {
            val mostSpecific = mostSpecificUtil.mostSpecificForResolveResult(sameNameCandidates)
            mostSpecific.fold(sameNameCandidates)(result => View(result))
          } else {
            // Implicit-scope candidates are not partitioned by kind, so this group can contain an
            // `extension ... def choose` together with a `given choose: Conversion[...]`. Keep such
            // a group intact: later resolution applies category-specific precedence, while comparing
            // extensions and conversions as ordinary overloads here would be invalid.
            sameNameCandidates
          }
      }
      .to(mutable.HashSet)
  }

  private def collectCompatibleCandidates(
    candidates:                        collection.Set[ScalaResolveResult],
    withLocalTypeInference:            Boolean,
    allowExtensionReceiverConversions: Boolean = true,
  ): Set[ScalaResolveResult] = {
    val filteredCandidatesRaw = mutable.HashSet.empty[ScalaResolveResult]
    val extensionsFromGivens  = mutable.HashSet.empty[ScalaResolveResult]

    // Stage 1: perform a cheap conformance check before the more expensive processing below.
    // Keep possible extension carriers even when the carrier itself does not conform. For example,
    // `given syntax: Syntax` must survive if `Syntax` contains
    // `extension (receiver: Receiver) def render: Result`, although `Syntax` is not the requested
    // `Receiver => Result` conversion type.
    val iterator = candidates.iterator
    while (iterator.hasNext) {
      val c = iterator.next()

      if (canContainTargetMethod(c)) {
        filteredCandidatesRaw += c
      } else {
        filteredCandidatesRaw ++= checkCompatible(
          c,
          withLocalTypeInference,
          checkFast                         = true,
          allowExtensionReceiverConversions = allowExtensionReceiverConversions
        )
      }
    }

    val filteredCandidates = pickMostSpecificExtensions(filteredCandidatesRaw)
    var results = Set.empty[ScalaResolveResult]

    // Stage 2: fully check each surviving candidate and retain only non-dominated applicable results.
    // An ordinary candidate such as `given Ordering[Int]` can become a result directly. An extension
    // carrier such as `given syntax: Syntax` instead contributes `Syntax.render` to
    // `extensionsFromGivens`; the carrier's own type-conformance failure is not returned as a result.
    while (filteredCandidates.nonEmpty) {
      val next = mostSpecificUtil.nextMostSpecific(filteredCandidates)
      next match {
        case Some(c) =>
          filteredCandidates.remove(c)

          val compatible = checkCompatible(
            c,
            withLocalTypeInference,
            allowExtensionReceiverConversions = allowExtensionReceiverConversions
          )

          // In extension-search mode, inspect only ordinary implicit/given results as extension
          // carriers. For example, `given syntax: Syntax` may expose `Syntax.render`; an already
          // found `extension (receiver: Receiver) def syntax: Syntax` must not have its return type
          // inspected recursively for `render`.
          if (withExtensions && !c.isExtensionCall) {
            for {
              result <- compatible
            } {
              val extensions = collectExtensionsFromImplicitResult(result, extensionData)
              extensionsFromGivens ++= extensions
            }
          }

          //filter away candidates, which only got through compatibility check,
          //because they might contain extensions
          val afterExtensionPredicate = compatible.filter(isValidImplicitResult).flatMap(applyExtensionPredicate)

          afterExtensionPredicate match {
            case Some(current) =>
              val noLessSpecificThanCurrent = noLessSpecificThan(current)(_)
              filteredCandidates.filterInPlace(noLessSpecificThanCurrent)
              //this filter was added to make result deterministic
              results = results.filter(noLessSpecificThanCurrent)
              results = results + current
            case _ =>
          }
        case None => ()
      }
    }

    // Stage 3: fully check the extensions discovered while processing the surviving carriers.
    // For `given syntax: Syntax`, this validates `Syntax.render` against the actual `Receiver`,
    // applies the requested extension-name predicate, and merges the applicable extension by
    // specificity with results found directly in stage 2.
    val extensionsToCheck = pickMostSpecificExtensions(extensionsFromGivens)
    extensionsToCheck.foreach { extension =>
      val compatible = checkCompatible(
        extension,
        withLocalTypeInference,
        allowExtensionReceiverConversions = allowExtensionReceiverConversions
      )
      val applicable = compatible.flatMap(applyExtensionPredicate)
      applicable.foreach { current =>
        results = results.filter(noLessSpecificThan(current))
        results += current
      }
    }

    results
  }

  private def noLessSpecificThan(current: ScalaResolveResult)(srr: ScalaResolveResult): Boolean = {
    if (current.isExtensionCall && srr.isExtensionCall) true // handled in MethodResolveProcessor
    else {
      // Prefer extensions to implicit conversions, but not if the extension comes from inside some given instance.
      // But if the conversion is OLD STYLE implicit def, extension wins ALWAYS.
      val srrIsOldStyleImplicitDef = srr.element match {
        case _: ScGiven    => false
        case _: ScFunction => true
        case _             => false
      }

      val preferExtensionToConversion =
        current.isExtensionCall &&
          (!current.isExtensionFromGiven || srrIsOldStyleImplicitDef)

      if (preferExtensionToConversion) false // conversion `srr` is less specific than extension `current`
      else !mostSpecificUtil.isMoreSpecific(current, srr)
    }
  }

  /**
   * Apart from being located directly in the lexical or implicit scope, extensions
   * can also be located inside implicit/given definitions inside the aforementioned scopes.
   */
  private def collectExtensionsFromImplicitResult(
    result:        ScalaResolveResult,
    extensionData: Option[ExtensionConversionData]
  ): Set[ScalaResolveResult] = {
    val proc = new ExtensionProcessor(
      place,
      name          = extensionData.map(_.refName).getOrElse(""),
      forCompletion = forCompletion
    )

    val tp = InferUtil.extractImplicitParameterType(result)

    val unresolvedTypeParams = result.unresolvedTypeParameters

    tp.foreach { t =>
      val state = ScalaResolveState
        .withImplicitScopeType(t)
        .withImportsUsed(result.importsUsed)

      val stateWithUnresolved = unresolvedTypeParams match {
        case Some(params) => state.withUnresolvedTypeParams(params)
        case None         => state
      }

      proc.processType(t, place, stateWithUnresolved)
    }

    proc.candidatesS
  }

  //@TODO: apply context function to implicit args if type of `c` does not conform
  //       to expected type
  private def simpleConformanceCheck(c: ScalaResolveResult): Option[ScalaResolveResult] = {
    val ty = c.element match {
      case param: ScParameter => param.insideParamType
      case typeable: Typeable => typeable.`type`()
      case _ => return None
    }

    val subst = c.substitutor
    ty match {
      case Right(t) =>
        val conformance = subst(t).conforms(tp, ConstraintSystem.empty)(Context(place))
        conformance match {
          case ConstraintSystem(subst) =>
            //Update synthetic parameters, coming from expected context-function type
            c.element match {
              case contextParam: LightContextFunctionParameter if !isImplicitConversion =>
                contextParam.updateWithSubst(subst)
              case _ => ()
            }

            Option(c.copy(implicitReason = OkResult))
          case _ =>
            reportWrong(c, TypeDoesntConformResult, propagateFailures = withExtensions)
        }
      case _ => reportWrong(c, BadTypeResult, propagateFailures = withExtensions)
    }
  }

  private def filterTypeParamsAndInferValueType(
    tp:             ScType,
    inferValueType: Boolean = true
  ): (ScType, Seq[TypeParameter]) = {
    if (!inferValueType) (tp, Seq.empty)
    else  {
      if (isExtensionConversion) {
        tp match {
          case ScTypePolymorphicType(internalType, typeParams) =>
            val filteredTypeParams =
              typeParams.filter(tp => !tp.lowerType.equiv(Nothing) || !tp.upperType.equiv(Any))
            val newPolymorphicType = ScTypePolymorphicType(internalType, filteredTypeParams)
            val updated = newPolymorphicType.inferValueType.updateLeaves {
              case u: UndefinedType => u.inferValueType
            }
            (updated, typeParams)
          case _ => (tp.inferValueType, Seq.empty)
        }
      } else tp match {
        case ScTypePolymorphicType(_, typeParams) => (tp.inferValueType, typeParams)
        case _ => (tp.inferValueType, Seq.empty)
      }
    }
  }

  private def updateNonValueType(nonValueType0: ScType): ScType = {
    InferUtil.updateAccordingToExpectedType(
      nonValueType0,
      filterTypeParams         = isImplicitConversion,
      expectedType             = Some(tp),
      expr                     = place,
      canThrowSCE              = true,
      shouldTruncateMethodType = false
    )
  }

  private def adaptAndApplyToImplicitArgs(
    c:                      ScalaResolveResult,
    nonValueType0:          ScType,
    hasImplicitClause:      Boolean,
    hadDependents:          Boolean,
    conformanceConstraints: ConstraintSystem,
    isLeadingImplicitsCase: Boolean
  ): Option[ScalaResolveResult] = {
    val fun            = c.element.asInstanceOf[ScFunction]
    val canContainExts = canContainTargetMethod(c)

    def wrongTypeParam(nonValueType: ScType, result: ImplicitResult): Option[ScalaResolveResult] = {
      val (valueType, typeParams) = filterTypeParamsAndInferValueType(nonValueType)

      Option(c.copy(
        problems                 = Seq(WrongTypeParameterInferred),
        inferredType             = Option(valueType),
        implicitReason           = result,
        unresolvedTypeParameters = Option(typeParams)
      ))
    }

    def reportParamNotFoundResult(
      resType:            ScType,
      implicitArgClauses: Seq[ImplicitArgumentsClause]
    ): Option[ScalaResolveResult] = {
      val (valueType, typeParams) = filterTypeParamsAndInferValueType(resType)

      val problems =
        for {
          clause  <- implicitArgClauses
          arg     <- clause.args
          problem <- arg.problems
        } yield problem

      val isOnlyProblemAmbiguity = problems.forall(_.is[AmbiguousImplicitParameters])

      reportWrong(
        c.copy(
          implicitArguments        = implicitArgClauses,
          inferredType             = Option(valueType),
          unresolvedTypeParameters = Option(typeParams)
        ),
        ImplicitParameterNotFoundResult,
        problems          = problems,
        propagateFailures = isOnlyProblemAmbiguity
      )
    }

    def noImplicitParametersResult(nonValueType: ScType): Option[ScalaResolveResult] = {
      val (valueType, typeParams) = filterTypeParamsAndInferValueType(nonValueType, !isLeadingImplicitsCase)

      val subst = conformanceConstraints.instantiationSubstOrEmpty

      val result = c.copy(
        subst                    = c.substitutor.followed(subst),
        inferredType             = Option(valueType),
        implicitReason           = OkResult,
        unresolvedTypeParameters = Option(typeParams)
      )
      Option(result)
    }

    def fullResult(
      resType:            ScType,
      implicitArgClauses: Seq[ImplicitArgumentsClause],
      constraints:        ConstraintSystem,
      checkConformance:   Boolean = false
    ): Option[ScalaResolveResult] = {
      val (valueType, typeParams) = filterTypeParamsAndInferValueType(resType, inferValueType = !isLeadingImplicitsCase)
      val allConstraints          = constraints + conformanceConstraints

      val constraintSubst = allConstraints.toInstantiationSubst

      constraintSubst.fold(reportWrong(c, CantInferTypeParameterResult)) { subst =>
        val allImportsUsed =
          implicitArgClauses
            .flatMap(_.args)
            .map(_.importsUsed)
            .foldLeft(c.importsUsed)(_ ++ _)

        val result = c.copy(
          subst                    = c.substitutor.followed(subst),
          inferredType             = Option(valueType),
          implicitArguments        = c.implicitArguments ++ implicitArgClauses,
          implicitReason           = OkResult,
          unresolvedTypeParameters = Option(typeParams),
          importsUsed              = allImportsUsed
        )

        if (checkConformance && !valueType.conforms(tp))
          reportWrong(result, TypeDoesntConformResult, Seq(WrongTypeParameterInferred))
        else Option(result)
      }
    }

    def wrongExtensionConversion(nonValueType: ScType): Option[ScalaResolveResult] = {
      if (extensionData.isEmpty) None
      else
        filterTypeParamsAndInferValueType(nonValueType) match {
          case (FunctionType(rt, _), _) =>
            val newCandidate = c.copy(inferredType = Some(rt))
            if (applyExtensionPredicate(newCandidate).isEmpty)
              wrongTypeParam(nonValueType, CantFindExtensionMethodResult)
            else None
          //this is not a function, when we still need to pass implicit?..
          case _ => wrongTypeParam(nonValueType, UnhandledResult)
        }
    }

    val (nonValueType, failedPtAdapt) = {
      try {
        val updated =
          if (isLeadingImplicitsCase) nonValueType0
          else                        updateNonValueType(nonValueType0)

        val noDependents =
          if (hadDependents) UndefinedType.revertDependentTypes(updated)
          else               updated

        val propagatedError = Option.when(c.implicitReason != NoResult && c.implicitReason != OkResult){
          val (_, unresolvedTps) = filterTypeParamsAndInferValueType(nonValueType0)

          if (unresolvedTps.isEmpty) c
          else                       c.copy(unresolvedTypeParameters = Option(unresolvedTps))
        }

        (noDependents, propagatedError)
      }
      catch {
        case _: SafeCheckException =>
          val result = wrongTypeParam(nonValueType0, CantInferTypeParameterResult)

          if (canContainExts) (nonValueType0, result)
          else                return result
      }
    }

    val depth            = ScalaProjectSettings.getInstance(project).getImplicitParametersSearchDepth
    val notTooDeepSearch = depth < 0 || recursionDepth < depth

    if (hasImplicitClause && notTooDeepSearch) {
      val conversionDataCheckedResult =
        if (!hadDependents) {
          val noMethod = wrongExtensionConversion(nonValueType)
          failedPtAdapt.orElse(noMethod)
        } else failedPtAdapt

      if (!canContainExts) {
        conversionDataCheckedResult.foreach(result => return Option(result))
      }

      val throwOnAmbiguous =
        !place.isInScala3File || conversionDataCheckedResult.nonEmpty

      try {
        val (resType, implicitArgsByClause) =
          InferUtil.updateTypeWithImplicitParameters(
            nonValueType,
            place,
            Option(fun),
            canThrowSCE            = !fullInfo,
            throwOnAmbiguous       = throwOnAmbiguous,
            implicitRecursionDepth = recursionDepth + 1,
            fullInfo               = fullInfo,
            updateDeep             = !isLeadingImplicitsCase,
            isLeadingClause        = isLeadingImplicitsCase
          )

        val implicitArgs = implicitArgsByClause.flatMap(_.args)
        val constraints  = implicitArgsByClause.map(_.constraints).foldLeft(ConstraintSystem.empty)(_ + _)

        if (implicitArgs.exists(_.isImplicitParameterProblem))
          reportParamNotFoundResult(resType, implicitArgsByClause)
        else
          conversionDataCheckedResult match {
            case Some(earlierError) =>
              constraints.toInstantiationSubst.fold(earlierError)(constraintSubst =>
                earlierError.copy(subst = earlierError.substitutor.followed(constraintSubst))
              ).toOption
            case _ =>
              fullResult(
                resType,
                implicitArgsByClause,
                constraints,
                checkConformance = hadDependents && !isLeadingImplicitsCase
              )
          }
      } catch {
        case _: SafeCheckException => wrongTypeParam(nonValueType, CantInferTypeParameterResult)
      }
    } else {
      failedPtAdapt.orElse(
        noImplicitParametersResult(nonValueType)
      )
    }
  }

  private def adaptAndApplyToImplicitArgsWithDivergenceChecker(
    c:                      ScalaResolveResult,
    methodType:             Option[ScType],
    hasImplicitClause:      Boolean,
    hadDependents:          Boolean,
    constraints:            ConstraintSystem,
    isLeadingImplicitsCase: Boolean
  ): Option[ScalaResolveResult] = measure("ImplicitCollector.adaptAndApplyToImplicitArgsWithDivergenceChecker") {
    def compute(): Option[ScalaResolveResult] = {
      methodType match {
        case None =>
          val constraintsSubst = constraints.instantiationSubstOrEmpty
          val withSubst        = c.copy(subst = c.substitutor.followed(constraintsSubst))

          if (c.implicitReason != NoResult) Option(withSubst)
          else                              Option(withSubst.copy(implicitReason = OkResult))

        case Some(mt) =>
          try {
            adaptAndApplyToImplicitArgs(
              c,
              c.substitutor(mt),
              hasImplicitClause,
              hadDependents,
              constraints,
              isLeadingImplicitsCase = isLeadingImplicitsCase
            )
          }
          catch {
            case _: SafeCheckException =>
              Option(
                c.copy(
                  problems       = Seq(WrongTypeParameterInferred),
                  implicitReason = UnhandledResult
                )
              )
          }
      }
    }

    if (isImplicitConversion) compute()
    else {
      val element = coreElement.getOrElse(place)

      def divergedResult = reportWrong(c, DivergedImplicitResult)

      DivergenceChecker.withDivergenceCheck(element, tp, divergedResult) {
        compute().orElse(divergedResult)
      }
    }
  }

  private def reportWrong(
    c:                 ScalaResolveResult,
    reason:            ImplicitResult,
    problems:          Seq[ApplicabilityProblem] = Seq.empty,
    propagateFailures: Boolean                   = false
  ): Option[ScalaResolveResult] =
    if (fullInfo || propagateFailures) Option(c.copy(problems = problems, implicitReason = reason))
    else                               None

  private def isPredefConforms(fun: ScFunction) = {
    val name = fun.name
    val clazz = fun.containingClass
    (name == "conforms" || name == "$conforms") && clazz != null && clazz.qualifiedName == "scala.Predef"
  }

  /**
   * Checks whether a function-like implicit candidate is compatible with the current implicit search target [[tp]] at [[place]].
   *
   * The method performs macro-aware function type extraction, expected-type conformance checks, and, when needed,
   * applies candidate implicit clauses to produce the final candidate state used by implicit search.
   * If [[isImplicitConversion]] is enabled, conversion-specific conformance and adaptation rules are applied.
   * It delegates to the private overload with precomputed type parameters.
   *
   * @param c                      implicit candidate being checked (regular implicit function or extension candidate).
   * @param withLocalTypeInference enables inferring unresolved candidate type parameters from the current implicit-search
   *                               context (expected type [[tp]] and extension/adaptation constraints) during conformance checks.
   * @param checkFast              enables a lightweight compatibility pass used for early filtering; in this mode some expensive
   *                               adaptation steps (for example, leading implicit-clause application) are intentionally skipped.
   * @return compatible candidate (possibly updated with inferred substitutor / implicit reason), or `None` if the
   *         candidate does not match and no diagnostic result needs to be propagated.
   */
  def checkFunctionTypeConformance(
    c:                      ScalaResolveResult,
    withLocalTypeInference: Boolean,
    checkFast:              Boolean,
  ): Option[ScalaResolveResult] = {
    val fun                 = c.element.asInstanceOf[ScFunction]
    val exportedInExtension = c.exportedInExtension
    val typeParams          = fun.typeParametersWithExtension(exportedInExtension)

    checkFunctionTypeConformance(c, withLocalTypeInference, checkFast, typeParams)
  }

  private def checkFunctionTypeConformance(
    c:                                 ScalaResolveResult,
    withLocalTypeInference:            Boolean,
    checkFast:                         Boolean,
    typeParams:                        Seq[ScTypeParam],
    allowExtensionReceiverConversions: Boolean = true,
  ): Option[ScalaResolveResult] = measure("ImplicitCollector.checkFunctionByType") {
    implicit val elementScope: ElementScope = c.element.elementScope

    val fun = c.element.asInstanceOf[ScFunction]
    val exportedInExtension = c.exportedInExtension
    if (typeParams.isEmpty || withLocalTypeInference) {
      //continue
    } else {
      return None
    }

    // We get here only when candidate type params are already fixed (no type params) or may be inferred locally.
    // Otherwise, generic candidates are deferred because conformance/adaptation below depends on instantiated type arguments.
    // Below we resolve the candidate's effective function type and try to adapt/apply its implicit clauses against the expected search type.

    val macroEvaluator = ScalaMacroEvaluator.getInstance(project)
    val typeFromMacro  = macroEvaluator.checkMacro(fun, MacroContext(place, Some(tp)))

    val undefineGivenInstanceParameters =
      if (c.isExtensionCall) {
        val typeParams = c.unresolvedTypeParameters.getOrElse(Seq.empty)
        ScSubstitutor.bind(typeParams)(UndefinedType(_))
      }
      else ScSubstitutor.empty

    val nonValueFunctionTypes =
      ImplicitCollector.cache(project).getNonValueTypes(
        fun,
        c.substitutor.followed(undefineGivenInstanceParameters),
        exportedInExtension,
        typeFromMacro
      )

    val shouldApplyToLeadingImplicits =
      !checkFast &&
        isImplicitConversion &&
        place.isInScala3File &&
        nonValueFunctionTypes.hasLeadingImplicitClause &&
        hasExplicitClause(c)

    val appliedToLeadingImplicits =
      if (shouldApplyToLeadingImplicits) {
        adaptAndApplyToImplicitArgsWithDivergenceChecker(
          c,
          nonValueFunctionTypes.methodType,
          nonValueFunctionTypes.hasImplicitClause,
          nonValueFunctionTypes.hadDependents,
          ConstraintSystem.empty,
          isLeadingImplicitsCase = true
        )
    } else Option(c)

    appliedToLeadingImplicits match {
      case Some(res) if res.implicitReason != NoResult && res.implicitReason != OkResult =>
        //failed to apply to leading implicits => fail
        return Option(res)
      case Some(cand) =>
        nonValueFunctionTypes.undefinedType match {
          case Some(undefined0: ScType) =>
            val substWithLeadingImplicits = cand.substitutor

            val undefined = substWithLeadingImplicits(
              undefined0 match {
                case Scala3Conversion(argType, resType) if isImplicitConversion => FunctionType(resType, Seq(argType))
                case _                                                          => undefined0
              }
            )

            val undefinedConforms =
              if (isImplicitConversion) {
                val pt = maskTypeParametersInExtensions(tp, cand)
                if (cand.isExtensionCall)
                  checkExtensionConformance(place, undefined, pt, allowExtensionReceiverConversions)
                else
                  checkWeakConformance(place, undefined, pt)
              } else undefined.conforms(tp, ConstraintSystem.empty)

            val methodType =
              if (shouldApplyToLeadingImplicits) {
                //If we applied the original candidate to its leading implicit arguments,
                //nonValueFunctionTypes.method type is no longer valid => use inferredType instead
                cand.inferredType
              } else nonValueFunctionTypes.methodType

            //are there any implicit param clauses left, after we have applied the candidate to the leading ones.
            val hasTrailingImplicits =
              (nonValueFunctionTypes.hasImplicitClause && !shouldApplyToLeadingImplicits) ||
                nonValueFunctionTypes.hasTrailingImplicitClause

            if (undefinedConforms.isRight) {
              if (checkFast)
                appliedToLeadingImplicits
              else
                adaptAndApplyToImplicitArgsWithDivergenceChecker(
                  cand,
                  methodType,
                  hasTrailingImplicits,
                  nonValueFunctionTypes.hadDependents,
                  undefinedConforms.constraints,
                  isLeadingImplicitsCase = false
                )
            } else if (canContainTargetMethod(cand)) {
              //With the addition of extensions in Scala 3,
              //we now cannot discard implicits based by their type right away,
              //because they might contain extensions, defined on their "return type".
              //So here and further down the function call tree we will not abort on
              //non-fatal failures (everything except for not-found-implicit-parameters problems)
              //and instead propagate them to the very end.
              adaptAndApplyToImplicitArgsWithDivergenceChecker(
                cand.copy(implicitReason = TypeDoesntConformResult),
                methodType,
                hasTrailingImplicits,
                nonValueFunctionTypes.hadDependents,
                undefinedConforms.constraints,
                isLeadingImplicitsCase = false
              )
            } else reportWrong(cand, TypeDoesntConformResult)
          case _ =>
            if (!withLocalTypeInference) reportWrong(cand, BadTypeResult)
            else                         None
        }
      case None => None
    }
  }

  /**
   * Checks whether the actual qualifier encoded by `pt` conforms to the receiver encoded by `tpe`.
   * Direct subtype conformance succeeds without another implicit search:
   * {{{
   *   class Parent
   *   class Child extends Parent
   *   extension (receiver: Parent) def label: String = "parent"
   *
   *   (new Child).label
   * }}}
   *
   * When receiver conversions are allowed, a single implicit conversion can make an otherwise
   * incompatible qualifier applicable:
   * {{{
   *   class Source
   *   class Target
   *   given Conversion[Source, Target] = _ => new Target
   *   extension (receiver: Target) def label: String = "target"
   *
   *   (new Source).label
   * }}}
   *
   * @param place call site used for type context and the nested implicit-conversion search
   * @param tpe candidate extension function type; its first argument is the declared receiver type
   * @param pt expected function type synthesized for the call; its first argument is the actual
   *           qualifier type
   * @param allowReceiverConversion whether to search for a conversion from the actual qualifier to
   *                                the declared receiver after direct conformance fails
   * @return the direct conformance constraints, an empty constraint system when exactly one receiver
   *         conversion is found, or a failed result otherwise
   */
  private def checkExtensionConformance(
    place:                   PsiElement,
    tpe:                     ScType,
    pt:                      ScType,
    allowReceiverConversion: Boolean,
  ): ConstraintsResult = {
    implicit val elementScope: ElementScope = place.elementScope
    implicit val context: Context = Context(place)

    val conformanceResult =
      for {
        (extensionArg, _) <- extractFunction1TypeArgs(tpe, strict = false)
        (ptArg, _)        <- extractFunction1TypeArgs(pt)
      } yield {
        val conforms = ptArg.conforms(extensionArg, ConstraintSystem.empty)

        if (conforms.isRight) conforms
        else if (allowReceiverConversion) {
          // A Scala 3 extension can still be applicable when an implicit conversion adapts the
          // qualifier from `ptArg` to its declared `extensionArg` receiver. Search for exactly one
          // such `ptArg => extensionArg` conversion here. The direct receiver phase passes `false`
          // to avoid letting an adapted receiver compete with a directly conforming one; only the
          // fallback phase is allowed to perform this nested implicit search.
          val conversionType = FunctionType(extensionArg, Seq(ptArg))

          val implicitCollector = new ImplicitCollector(
            place,
            conversionType,
            conversionType,
            None,
            isImplicitConversion = true
          )

          val conversionCandidates = implicitCollector.collect()
          conversionCandidates match {
            case Seq(_) => ConstraintSystem.empty
            case _      => ConstraintsResult.Left
          }
        } else ConstraintsResult.Left
      }

    conformanceResult.getOrElse(ConstraintsResult.Left)
  }

  /**
   * This is a workaround to avoid accidental type parameter
   * capturing, when resolving an extension from inside itself, e.g.
   * {{{
   *   extension [A, B] (fa: F[A]) {
   *     def foo(b: B): A = ???
   *     def bar(fab: F[A => B]) = fab.foo
   *   }
   * }}}
   * Here `fab.foo` is problematic, bc unresolved type parameter `B`
   * is propagated to the `foo` method and later is replaced with undefined type,
   * but since `A` is set to `A => B` and all these [[TypeParameterType]]s point to the same
   * physical type parameters `B` in `A => B` is replaced with undefined type as well.
   * To avoid that, here each type parameter ref is replaced with a fresh one
   * lower & upper bounded by the old one (`B` -> `NewB >: B <: B`)
   *
   */
  private def maskTypeParametersInExtensions(tp: ScType, cand: ScalaResolveResult): ScType = {
    val extension = cand.element match {
      case m: ScFunction => m.extensionMethodOwner
      case _             => None
    }

    extension match {
      case Some(ext) =>
        val tpIds = ext.typeParameters.map(_.typeParamId)

        tp.updateRecursively {
          case tpt: TypeParameterType if tpIds.contains(tpt.psiTypeParameter.typeParamId) =>
            val newTp = TypeParameter.light(tpt.name, tpt.typeParameters, tpt, tpt)
            TypeParameterType(newTp)
        }
      case None => tp
    }
  }

  private def applyExtensionPredicate(cand: ScalaResolveResult): Option[ScalaResolveResult] = {
    extensionData match {
      case None => Some(cand)
      case Some(data) =>
        val applicabilityCheck =
          if (cand.isExtensionCall) {
            val candName = cand.renamed.getOrElse(cand.name)
            Option.when(ScalaNamesUtil.equivalent(data.refName, candName))(cand)
          } else extensionConversionCheck(data, cand)

        applicabilityCheck.orElse(
          reportWrong(cand, CantFindExtensionMethodResult)
        )
    }
  }

  private def hasExplicitClause(srr: ScalaResolveResult): Boolean = srr.element match {
    case fun: ScFunction =>
      val exportedInExtension = srr.exportedInExtension
      fun.parameterClausesWithExtension(exportedInExtension).exists(!_.isImplicit)
    case _ => false
  }

  private def extractFunction1TypeArgs(scType: ScType, strict: Boolean = true): Option[(ScType, ScType)] = {
    import SmartSuperTypeUtil.{TraverseSupers, traverseSuperTypes}

    def isFunction1Class(cls: PsiClass): Boolean =
      cls.qualifiedName == "scala.Function1"

    scType match {
      case ParameterizedType(ScDesignatorType(c: PsiClass), args)
        if args.size == 2 && isFunction1Class(c) => (args.head, args.last).toOption
      case _ =>
        if (strict) None
        else {
          var res: Option[(ScType, ScType)] = None

          traverseSuperTypes(
            scType,
            (tpe, cls, _) => (tpe, cls) match {
              case (ParameterizedType(_, args), cls)
                if args.size == 2 && isFunction1Class(cls) =>
                res = (args.head, args.last).toOption
                TraverseSupers.Stop
              case _ => TraverseSupers.ProcessParents
            }
          )

          res
        }

    }
  }

  private def checkWeakConformance(place: PsiElement, tpe: ScType, pt: ScType): ConstraintsResult = {
    implicit val context: Context = Context(place)

    extractFunction1TypeArgs(tpe, strict = false) match {
      case Some((tpeArg, tpeRes)) =>
        extractFunction1TypeArgs(pt) match {
          case Some((ptArg, ptRes)) =>
            ptArg.conforms(tpeArg, ConstraintSystem.empty, checkWeak = true) match {
              case cs: ConstraintSystem => tpeRes.conforms(ptRes, cs)
              case left                 => left
            }
          case _ => ConstraintsResult.Left
        }
      case _ => ConstraintsResult.Left
    }
  }
}
