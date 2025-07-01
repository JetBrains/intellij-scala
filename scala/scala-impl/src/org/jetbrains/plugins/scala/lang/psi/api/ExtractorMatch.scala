package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScExtractorPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, FunctionType, NamedTupleType, ParameterizedType, TupleType}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, Context, ScType, ScalaSeqExt, api}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.MapView

sealed abstract class ExtractorMatch {
  /**
   * Checks whether this extractor match can be used when the patterns with the given shape are used in the extractor pattern
   */
  def isApplicable(shape: ScExtractorPattern.ArgPatternShape): Boolean

  /**
   * Returns the types in correct order to be used in patterns in the extractor pattern
   */
  def productTypes: Seq[ScType]

  /**
   * Returns the type of the sequence when a variadic match is used
   */
  def sequenceTypeOption: Option[ScType]

  /**
   * Returns whether this extractor match supports taking any parameters
   */
  def isEmpty: Boolean

  /**
   * Whether the extractor match has a selector type that supports named patterns
   */
  def supportsNamedPatterns: Boolean

  /**
   * Returns a lazy mapping of names to types that may be used in named patterns
   */
  def namedPatternTypes(place: PsiElement): MapView[String, Option[ScType]]

  /**
   * Returns the type from which the productTypes/namedPatternTypes come from
   */
  def selectorType: ScType

  /**
   * Returns whether this match will always match or not
   */
  def isIrrefutable(shape: ScExtractorPattern.ArgPatternShape): Boolean
}

//noinspection NameBooleanParameters
object ExtractorMatch {
  sealed trait Unapply extends ExtractorMatch {
    def productTypes: Seq[ScType]

    def place: PsiElement

    final lazy val lastTypeIsSeq: Boolean =
      productTypes.lastOption.exists { p =>
        val isSeqLike = SeqLikeType(place)
        isSeqLike.unapply(p).isDefined
      }

    override final def isApplicable(shape: ScExtractorPattern.ArgPatternShape): Boolean =
      if (shape.hasNamedArgs) supportsNamedPatterns
      else if (shape.totalArgCount != productTypes.length) false
      else if (shape.seqAtEnd) lastTypeIsSeq
      else true

    override final def sequenceTypeOption: None.type = None
    override final def isEmpty: Boolean = productTypes.isEmpty
  }

  object Unapply {
    def boolean(tpe: ScType): Unapply = product(Seq.empty, tpe, tpe.equivalentToLiteral(true), null)

    def tuple(tuple: ScType, comps: Seq[ScType], irrefutable: Boolean, p: PsiElement): Unapply = new Unapply {
      override def productTypes: Seq[ScType] = comps
      override def place: PsiElement = p
      override def supportsNamedPatterns: Boolean = false
      override def namedPatternTypes(place: PsiElement): MapView[String, Option[ScType]] = MapView.empty
      override def selectorType: ScType = tuple
      override def isIrrefutable(shape: ScExtractorPattern.ArgPatternShape): Boolean = irrefutable
    }

    def namedTuple(nt: ScType, comps: Seq[(ScType, ScType)], irrefutable: Boolean, p: PsiElement): Unapply = new Unapply {
      override lazy val productTypes: Seq[ScType] = comps.map(_._1)
      override def place: PsiElement = p
      override def supportsNamedPatterns: Boolean = true
      override def namedPatternTypes(place: PsiElement): MapView[String, Option[ScType]] =
        NamedTupleType.makeComponentMap(comps).view.mapValues(Some.apply)
      override def selectorType: ScType = nt
      override def isIrrefutable(shape: ScExtractorPattern.ArgPatternShape): Boolean = irrefutable
    }

    def product(pTypes: Seq[ScType], selType: ScType, irrefutable: Boolean, p: PsiElement): Unapply = new Unapply {
      override val productTypes: Seq[ScType] = pTypes
      override def place: PsiElement = p
      override def supportsNamedPatterns: Boolean = false
      override def namedPatternTypes(place: PsiElement): MapView[String, Option[ScType]] = MapView.empty
      override def selectorType: ScType = selType
      override def isIrrefutable(shape: ScExtractorPattern.ArgPatternShape): Boolean = irrefutable
    }

    def productWithSelector(pTypes: Seq[ScType], selType: ScType, irrefutable: Boolean, p: PsiElement): Unapply = new Unapply {
      override val productTypes: Seq[ScType] = pTypes
      override def place: PsiElement = p
      override def supportsNamedPatterns: Boolean = namedPatternTypesCalculator.isDefined
      override def namedPatternTypes(place: PsiElement): MapView[String, Option[ScType]] =
        namedPatternTypesCalculator match {
          case Some(namedTypesCalculator) => namedTypesCalculator(place)
          case None => MapView.empty
        }
      override def selectorType: ScType = selType
      override def isIrrefutable(shape: ScExtractorPattern.ArgPatternShape): Boolean = irrefutable

      private lazy val namedPatternTypesCalculator: Option[PsiElement => MapView[String, Option[ScType]]] = {
        selType.extractClass
          .collect { case clazz: ScClass if clazz.isCase => clazz }
          .flatMap(clazz => clazz.allClauses.headOption)
          .map(_.parameters)
          .filter(_.nonEmpty)
          .map { params =>
            place => {
              final class Lazy(name: String) {
                lazy val paramType: Option[ScType] = findMember(name, selType, place)
              }
              params
                .map(_.name)
                .map(name => name -> new Lazy(name))
                .to(Map)
                .view
                .mapValues(l => l.paramType)
            }
          }
      }
    }
  }

  // for types like (String, Boolean, Seq[Int])
  // for types like Seq[Int], productTypes is empty
  final case class UnapplySeq(override val productTypes: Seq[ScType], sequenceType: ScType, override val selectorType: ScType, irrefutable: Boolean) extends ExtractorMatch {
    def minSubPatternCount: Int = productTypes.length

    override def isApplicable(shape: ScExtractorPattern.ArgPatternShape): Boolean =
      shape.nonSeqArgCount >= minSubPatternCount
    override def isEmpty: false = false
    override def sequenceTypeOption: Some[ScType] = Some(sequenceType)
    override def supportsNamedPatterns: false = false
    override def namedPatternTypes(place: PsiElement): MapView[String, Option[ScType]] = MapView.empty
    override def isIrrefutable(shape: ScExtractorPattern.ArgPatternShape): Boolean =
      irrefutable && shape.seqAtEnd && shape.nonSeqArgCount == productTypes.length
  }

  /**
   * Returns the best extractor match for the given pattern count.
   * "best match" is defined as:
   * 1. if there are applicable matches, the one with the highest precedence is returned
   * 2. otherwise the match with the least missing patterns is returned
   * 3. if there are only matches with too few patterns, the one with the most patterns is returned
   */
  def bestMatch[T <: ExtractorMatch](matches: LazyList[T], shape: ScExtractorPattern.ArgPatternShape): Option[T] = {
    def matchWithLeastMissingPatterns = matches.iterator
      .filter(_.productTypes.length >= shape.nonSeqArgCount)
      .minByOption(_.productTypes.length)

    def matchWithMostPatterns = matches
      .maxByOption(_.productTypes.length)

    matches.findApplicable(shape)
      .orElse(matchWithLeastMissingPatterns)
      .orElse(matchWithMostPatterns)
  }

  implicit class LazyListExt[T <: ExtractorMatch](private val list: LazyList[T]) extends AnyVal {
    def bestMatch(shape: ScExtractorPattern.ArgPatternShape): Option[T] = ExtractorMatch.bestMatch(list, shape)
    def hasApplicable(shape: ScExtractorPattern.ArgPatternShape): Boolean = list.exists(_.isApplicable(shape))
    def findApplicable(shape: ScExtractorPattern.ArgPatternShape): Option[T] = list.find(_.isApplicable(shape))
  }

  private def findMember(name: String, tp: ScType, place: PsiElement, parameterless: Boolean = true): Option[ScType] = {
    val variants = CompletionProcessor.variantsWithName(tp, place, name)

    variants.flatMap {
      case ScalaResolveResult(fun: ScFunction, subst)
        if (!parameterless || fun.parameters.isEmpty) && fun.name == name =>
        Seq(subst(fun.`type`().getOrAny))
      case ScalaResolveResult(b: ScBindingPattern, subst) if b.name == name =>
        Seq(subst(b.`type`().getOrAny))
      case ScalaResolveResult(param: ScClassParameter, subst) if param.name == name =>
        Seq(subst(param.`type`().getOrAny))
      case _ => Seq.empty
    }.headOption
  }

  def extractPossibleProductParts(receiverType: ScType, place: PsiElement): Seq[ScType] = {
    val builder = Seq.newBuilder[ScType]

    @tailrec
    def collect(i: Int): Unit = findMember(s"_$i", receiverType, place) match {
      case Some(tp) => builder += tp; collect(i + 1)
      case _        => ()
    }

    collect(1)
    builder.result()
  }

  case class ByNameExtractor(place: PsiElement) {
    def unapply(tpe: ScType): Option[Seq[ScType]] = {
      val selectors = extractPossibleProductParts(tpe, place)
      if (selectors.length >= 2) Some(selectors)
      else                       None
    }
  }

  private[this] case class ApplyBasedExtractor(place: PsiElement) {
    private implicit def context: Context = Context(place)

    def unapply(tpe: ScType): Option[ScType] =
      for {
        apply <- findMember(CommonNames.Apply, tpe, place, parameterless = false)
        resTpe <- apply match {
          case FunctionType(res, Seq(idxTpe)) if idxTpe.equiv(api.Int(place)) =>
            res.toOption
          case _ => None
        }
      } yield resTpe
  }

  private[this] case class SeqLikeType(place: PsiElement) {
    private implicit def context: Context = Context(place)

    private[this] val seqFqn = place.scalaSeqFqn

    def unapply(tpe: ScType): Option[ScType] = {
      val baseTpes = Iterator(tpe) ++ BaseTypes.iterator(tpe)
      baseTpes.collectFirst {
        case ParameterizedType(ExtractClass(cls), args)
          if args.length == 1 && cls.qualifiedName == seqFqn => args.head
      }
    }
  }

  private[this] def extractedType(returnTpe: ScType, place: PsiElement, fun: ScFunction): Option[(ScType, Boolean)] = {
    implicit val context: Context = Context(place)

    returnTpe match {
      case ParameterizedType(ExtractClass(cls), Seq(arg))
        if cls.qualifiedName == "scala.Option" || cls.qualifiedName == "scala.Some" =>
        val irrefutable =
          cls.qualifiedName == "scala.Some" ||
            (fun.syntheticCaseClass.nonEmpty && fun.isSynthetic) // for all unapply methods from case classes in scala 3
        Some(arg -> irrefutable)
      case other =>
        for {
          isEmpty   <- findMember("isEmpty", other, place)
          extracted <- findMember("get", other, place)
        } yield (extracted, isEmpty.equivalentToLiteral(false))
    }
  }

  /*
   * Checks if `tpe` conforms to the following interface and returns T1
   * {
   *   def lengthCompare(len: Int): Int // or, `def length: Int`
   *   def apply(i: Int): T1
   *   def drop(n: Int): scala.Seq[_]
   *   def toSeq: scala.Seq[_]
   * }
   */
  private[this] def extractSequenceMatchType(tpe: ScType, place: PsiElement): Option[ScType] = {
    val applyReturnTpe = ApplyBasedExtractor(place)
    for {
      _  <- findMember("lengthCompare", tpe, place, parameterless = false).orElse(findMember("length", tpe, place))
      _  <- findMember("drop", tpe, place, parameterless = false)
      _  <- findMember("toSeq", tpe, place)
      case applyReturnTpe(t1) <- Some(tpe)
    } yield t1
  }

  private[this] def extractSeqElementType(seqTpe: ScType, place: PsiElement): Option[ScType] = {
    lazy val applyBasedExtractor = ApplyBasedExtractor(place)
    lazy val seqLikeExtractor    = SeqLikeType(place)

    seqTpe match {
      case seqLikeExtractor(tpe)    => tpe.toOption
      case applyBasedExtractor(tpe) => tpe.toOption
      case _                        => None
    }
  }

  private def isProduct(tpe: ScType)(implicit context: Context): Boolean = {
    val productFqn = "scala.Product"
    val baseTpes = Iterator(tpe) ++ BaseTypes.iterator(tpe)
    baseTpes.exists {
      case ExtractClass(cls) if cls.qualifiedName == productFqn => true
      case _                                                    => false
    }
  }



  /**
   * Returns the types of subpatterns for all applicable matching methods in scala 3 in the order of precedence.
   * See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
   */
  private[this] def scala3UnapplyExtractorMatches(tpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.Unapply] = {
    implicit val projectContext: ProjectContext = place
    implicit val context: Context = Context(place)

    def withAutoTupling(unapply: Unapply): Seq[Unapply] =
      unapply.productTypes match {
        case Seq(t@TupleType(comps)) => Seq(Unapply.tuple(t, comps, irrefutable = true, place))
        case _                     => Seq.empty
      }

    def unapplyFromProductParts(tpe: ScType, irrefutable: Boolean): LazyList[Unapply] = {
      val productParts = extractPossibleProductParts(tpe, place)
      if (productParts.isEmpty) LazyList.empty
      else LazyList(Unapply.productWithSelector(productParts, tpe, irrefutable, place))
    }

    /*
     * Scala 3 boolean match
     */
    if (tpe.conforms(api.Boolean)) {
      // if tpe is a boolean then it cannot be any of the other matches
      // so we don't even need to try them and can just return
      return LazyList(Unapply.boolean(tpe))
    }

    /*
     * Scala 3 product match for types that implement scala.Product
     * and have _1..._N methods
     */
    lazy val productMatch = tpe match {
      case TupleType(comps)      => LazyList(Unapply.tuple(tpe, comps, irrefutable = true, place))
      case NamedTupleType(comps) => LazyList(Unapply.namedTuple(tpe, comps, irrefutable = true, place))
      case _ if isProduct(tpe)   => unapplyFromProductParts(tpe, irrefutable = true)
      case _                     => LazyList.empty
    }

    /*
     * Scala 3 single match for types that conform to
     * {
     *   def isEmpty: Boolean
     *   def get: S
     * }
     * // S is the single match
     */
    lazy val singleMatch = extractedType(tpe, place, fun).to(LazyList)

    /*
     * # Named based match
     * If there was a single match and S has _1, _2, ... _N methods (N >= 2),
     * then these can be matched as well
     */
    def nameBasedMatch: LazyList[Unapply] = singleMatch
      .flatMap { case (ty, irrefutable) =>
        ty match {
          case t@TupleType(comps) => if (comps.length >= 2) Seq(Unapply.tuple(t, comps, irrefutable, place)) else Seq.empty
          case nt@NamedTupleType(comps) => Seq(Unapply.namedTuple(nt, comps, irrefutable, place))
          case tpe => unapplyFromProductParts(tpe, irrefutable)
        }
      }


    productMatch #:::
      productMatch.flatMap(withAutoTupling) #:::
      singleMatch.map { case (single, irrefutable) => Unapply.product(Seq(single), single, irrefutable, place) } #:::
      nameBasedMatch
  }

  private def scala2UnapplyExtractorMatches(tpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.Unapply] = {
    implicit val projectContext: ProjectContext = place
    implicit val context: Context = Context(place)
    /*
     * Scala 2 boolean match
     */
    if (tpe.conforms(api.Boolean)) {
      // if tpe is a boolean then it cannot be any of the other matches
      // so we don't even need to try them and can just return
      return LazyList(Unapply.boolean(tpe))
    }

    val extractorTypeAndIrrefutability = extractedType(tpe, place, fun)

    /*
     * Scala 2 Constructor match
     *
     * If fun is the synthetic unapply method of a case class, then we have a Constructor Pattern.
     * In that case only the exact parameters of the first constructor clause are matched.
     * We still use the extractorType, because that has generic parameters resolved correctly.
     */
    fun.syntheticCaseClass match {
      case Some(caseClass) if fun.isSynthetic =>
        // Ok, we have a synthetic unapply method of a case class.
        // That means we have a Constructor Pattern.
        val hasOnlyOneParameter = caseClass.constructor.exists(_.effectiveFirstParameterSection.length == 1)
        val extractorType = extractorTypeAndIrrefutability.map(_._1)

        val comps = extractorType match {
          case Some(extractorType) if hasOnlyOneParameter => Seq(extractorType)
          case Some(TupleType(comps)) => comps
          case _ =>
            // Hmm... something went wrong...
            Seq.empty
        }
        return LazyList(Unapply.product(comps, extractorType.getOrElse(tpe), irrefutable = true, place))
      case _ => ()
    }

    extractorTypeAndIrrefutability match {
      case None => LazyList.empty
      case Some((extractorType, irrefutable)) =>
        /*
         * First the extractedType can be a matched itself
         */
        val extractorMatch = Unapply.product(Seq(extractorType), extractorType, irrefutable, place)

        /*
         * Next check if it has _1, _2, ... _N methods (N >= 2)
         */
        def nameBased = {
          val byNameExtractor = ByNameExtractor(place)
          extractorType match {
            case TupleType(comps)       => LazyList(Unapply.product(comps, extractorType, irrefutable, place))
            case byNameExtractor(comps) => LazyList(Unapply.product(comps, extractorType, irrefutable, place))
            case _                      => LazyList.empty
          }
        }

        extractorMatch #:: nameBased
    }
  }


  def unapplyExtractorMatches(returnTpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.Unapply] = {
    if (place.isInScala3File) scala3UnapplyExtractorMatches(returnTpe, place, fun)
    else scala2UnapplyExtractorMatches(returnTpe, place, fun)
  }

  /**
   * Returns the types of subpatterns for all applicable matching methods in scala 3 in the order of precedence.
   * See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
   */
  private[this] def scala3UnapplySeqMatches(tpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.UnapplySeq] = {
    implicit val context: Context = Context(place)

    // v is the V from https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
    def inner(v: ScType, extract: Boolean, irrefutable: Boolean): LazyList[ExtractorMatch.UnapplySeq] = {
      /**
       * Scala 3 sequence match for types that conform to sequence type (see [[extractSequenceMatchType]])
       */
      val sequenceMatch = extractSequenceMatchType(v, place).map(ExtractorMatch.UnapplySeq(Seq.empty, _, v, irrefutable))

      /**
       * Scala 3 product sequence match for types that implement Product and have _1..._N methods,
       * where N > 0 and _N conforms to the sequence type (see [[extractSequenceMatchType]])
       */
      def productSequenceMatch =
        if (!isProduct(v)) LazyList.empty
        else {
          val productComponents = extractPossibleProductParts(v, place)
          productComponents.lastOption
            .flatMap(extractSequenceMatchType(_, place))
            .map(ExtractorMatch.UnapplySeq(productComponents.init, _, v, irrefutable))
        }

      /**
       * If it is not a sequence match or product match we see if it conforms to the following
       * type and repeat whole procedure for the type returned by get
       * {
       *   def isEmpty: Boolean
       *   def get: S
       * }
       * // S is the new V
       */
      def extracted = extractedType(v, place, fun) match {
        case Some((tpe, irrefutable)) => inner(tpe, extract = false, irrefutable)
        case None                     => LazyList.empty
      }

      (
        sequenceMatch #::
          productSequenceMatch #::
          LazyList.empty
        ).flatten ++
        (if (extract) extracted else LazyList.empty)
    }

    inner(tpe, extract = true, irrefutable = true)
  }

  private def scala2UnapplySeqMatches(tpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.UnapplySeq] = {
    implicit val context: Context = Context(place)

    extractedType(tpe, place, fun) match {
      case None => LazyList.empty
      case Some((extractorType, irrefutable)) =>
        def typesToUnapplySeqMatch(types: Seq[ScType], selType: ScType): LazyList[ExtractorMatch.UnapplySeq] = {
          extractSeqElementType(types.last, place)
            .map(ExtractorMatch.UnapplySeq(types.init, _, selType, irrefutable))
            .to(LazyList)
        }

        /*
         * first check if the extracted type has _1, _2, ... _N methods (N >= 2) where _N conforms to Seq[_]
         *
         * example:
         *   def unapplySeq(a: A): Option[(String, Int, Seq[Boolean])]
         */
        val memberBased = {
          val byNameExtractor = ByNameExtractor(place)
          extractorType match {
            case TupleType(comps)       => typesToUnapplySeqMatch(comps, extractorType)
            case byNameExtractor(comps) => typesToUnapplySeqMatch(comps, extractorType)
            case _                      => LazyList.empty
          }
        }

        /*
          * if that didn't work, check if the extracted type itself conforms to Seq[_]
          * example:
          *   def unapplySeq(a: A): Option[Seq[Boolean]]
         */
        def extractorMatch = typesToUnapplySeqMatch(Seq(extractorType), extractorType)

        memberBased ++ extractorMatch
    }
  }

  def unapplySeqExtractorMatches(returnTpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.UnapplySeq] = {
    if (place.isInScala3File) scala3UnapplySeqMatches(returnTpe, place, fun)
    else scala2UnapplySeqMatches(returnTpe, place, fun)
  }

  /*
   * Returns all possible extractor matches for `returnTpe` returned by `fun` in `place`.
   * The matches are ordered by precedence (highest precedence first).
   */
  def extractorMatches(returnTpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch] = {
    if (fun.name == CommonNames.Unapply) unapplyExtractorMatches(returnTpe, place, fun)
    else                                 unapplySeqExtractorMatches(returnTpe, place, fun)
  }
}
