package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.FakeCompanionClassOrCompanionClass
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.PatternTypeInference
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompletionProcessor, ExpandedExtractorResolveProcessor}

import scala.annotation.tailrec
import scala.meta.intellij.QuasiquoteInferUtil

trait ScPattern extends ScalaPsiElement with Typeable {
  def isIrrefutableFor(t: Option[ScType]): Boolean

  def bindings: Seq[ScBindingPattern]

  def typeVariables: Seq[ScTypeVariableTypeElement]

  def subpatterns: Seq[ScPattern]

  def analogInDesugaredForExpr: Option[ScPattern]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPattern(this)
  }
}

object ScPattern {
  implicit class Ext(private val pattern: ScPattern) extends AnyVal {

    import pattern.{elementScope, projectContext}

    def expectedType: Option[ScType] = cachedInUserData("expectedType", pattern, BlockModificationTracker(pattern)) {
      _expectedType
    }

    // TODO Don't use the return keyword
    private def _expectedType: Option[ScType] = {
      val psiManager = ScalaPsiManager.instance

      pattern.getContext match {
        case list: ScPatternList =>
          list.getContext match {
            case _var: ScVariable => _var.`type`().toOption
            case _val: ScValue    => _val.`type`().toOption
          }
        case argList: ScPatternArgumentList =>
          argList.getContext match {
            case constr: ScConstructorPattern =>
              val thisIndex: Int = constr.args.patterns.indexWhere(_ == pattern)
              expectedTypeForExtractorArg(constr, constr.ref, thisIndex, constr.expectedType, argList.patterns.length)
            case _ => None
          }
        case composite: ScCompositePattern => composite.expectedType
        case infix: ScInfixPattern =>
          val i =
            if (infix.left == pattern) 0
            else if (pattern.is[ScTuplePattern]) return None //pattern is handled elsewhere in pattern function
            else 1
          expectedTypeForExtractorArg(infix, infix.operation, i, infix.expectedType, 2)
        case par: ScParenthesisedPattern => par.expectedType
        case patternList: ScPatterns => patternList.getContext match {
          case tuple: ScTuplePattern =>
            tuple.getContext match {
              case infix: ScInfixPattern =>
                if (infix.left != tuple) {
                  //so it's right pattern
                  val i = tuple.patternList match {
                    case Some(patterns: ScPatterns) => patterns.patterns.indexWhere(_ == pattern)
                    case _                          => return None
                  }

                  val patternLength: Int = tuple.patternList match {
                    case Some(pat) => pat.patterns.length
                    case _         => -1 //is it possible to get here?
                  }

                  return expectedTypeForExtractorArg(infix, infix.operation, i + 1, infix.expectedType, patternLength)
                }
              case _ =>
            }

            @tailrec
            def handleTupleSubpatternExpectedType(tupleExpectedType: ScType): Option[ScType] =
              tupleExpectedType match {
                case TupleType(comps) =>
                  val idx = patternList.patterns.indexWhere(_ == pattern)
                  comps.lift(idx)
                case et0 if et0.isAnyRef || et0.isAny => Some(Any)
                case ex: ScExistentialType =>
                  val simplified = ex.simplify()
                  if (simplified != ex) handleTupleSubpatternExpectedType(simplified)
                  else                  None
                case _                                => None
              }

            tuple.expectedType.flatMap(handleTupleSubpatternExpectedType)
          case _: ScXmlPattern =>
            val nodeClass: Option[PsiClass] = psiManager.getCachedClass(elementScope.scope, "scala.xml.Node")
            nodeClass.flatMap { nodeClass =>
              pattern match {
                case SeqExpectingPattern() =>
                  ScDesignatorType(nodeClass).wrapIntoSeqType
                case _ => Some(ScDesignatorType(nodeClass))
              }
            }
          case _ => None
        }
        case clause: ScCaseClause => clause.getContext /*clauses*/ .getContext match {
          case matchStat: ScMatch => matchStat.expression match {
            case Some(e) => Some(e.`type`().getOrAny)
            case _       => None
          }
          case b: ScBlockExpr if b.getContext.is[ScCatchBlock] =>
            val thr = psiManager.getCachedClass(elementScope.scope, "java.lang.Throwable")
            thr.map(ScalaType.designator(_))
          case b: ScBlockExpr =>
            val functionLikeType = FunctionLikeType(b)

            b.expectedType(fromUnderscore = false) match {
              case Some(et) =>
                et.removeAbstracts match {
                  case functionLikeType(_, _, Seq())   => Some(api.Unit)
                  case functionLikeType(_, _, Seq(p0)) => Some(p0)
                  case functionLikeType(_, _, params)  => Some(TupleType(params))
                  case _                               => None
                }
              case None => None
            }
          case _ => None
        }
        case named: ScNamingPattern           => named.expectedType
        case _: ScGenerator                   => pattern.analogInDesugaredForExpr.flatMap(_.expectedType)
        case forBinding: ScForBinding         => forBinding.expr.flatMap(_.`type`().toOption)
        case sc3TypedPattern: Sc3TypedPattern =>
          for {
            typePattern  <- sc3TypedPattern.typePattern
            ascribedType <- typePattern.typeElement.`type`().toOption
          } yield sc3TypedPattern.expectedType.fold(ascribedType)(_.glb(ascribedType))
        case _ => None
      }
    }

    private def expectedTypeForExtractorArg(
      contextPattern:        ScPattern,
      ref:                   ScStableCodeReference,
      argIndex:              Int,
      expected:              Option[ScType],
      totalNumberOfPatterns: Int
    ): Option[ScType] = {
      val bind =
        ExpandedExtractorResolveProcessor.resolveActualUnapply(ref)

      bind match {
        case Some(ScalaResolveResult(fun: ScFunction, _))
          if fun.name == CommonNames.Unapply && ScPattern.isQuasiquote(fun) =>
          val tpe = pattern.getContext.getContext match {
            case _: ScInterpolationPattern =>
              val parts = pattern.getParent.asInstanceOf[ScalaPsiElement]
                .findChildrenByType(ScalaTokenTypes.tINTERPOLATED_STRING)
                .map(_.getText)
              if (argIndex < parts.length && parts(argIndex).endsWith("..."))
                ScalaPsiElementFactory.createTypeElementFromText("Seq[Seq[scala.reflect.api.Trees#Tree]]", fun)
              if (argIndex < parts.length && parts(argIndex).endsWith(".."))
                ScalaPsiElementFactory.createTypeElementFromText("Seq[scala.reflect.api.Trees#Tree]", fun)
              else
                ScalaPsiElementFactory.createTypeElementFromText("scala.reflect.api.Trees#Tree", fun)
          }
          tpe.`type`().toOption
        case Some(ScalaResolveResult(fun: ScFunction, _))
          if fun.name == CommonNames.Unapply && QuasiquoteInferUtil.isMetaQQ(fun) =>
          try {
            val interpolationPattern = pattern.getParent.getParent.asInstanceOf[ScInterpolationPatternImpl]
            val patterns = QuasiquoteInferUtil.getMetaQQPatternTypes(interpolationPattern)
            if (argIndex < patterns.size) {
              val clazz = patterns(argIndex)
              val tpe = ScalaPsiElementFactory.createTypeElementFromText(clazz, fun)
              tpe.`type`().toOption
            } else None
          } catch {
            case _: ArrayIndexOutOfBoundsException => None // workaround for meta parser failure on malformed quasiquotes
          }
        case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor))
          if fun.name == CommonNames.Unapply && fun.parameters.count(!_.isImplicitParameter) == 1 =>

          val subst = expected match {
            case Some(tp) => substitutor.followed(PatternTypeInference.doTypeInference(contextPattern, tp))
            case _        => substitutor
          }

          fun.returnType match {
            case Right(rt) =>
              ScPattern.unapplyExtractorMatches(subst(rt), pattern, fun)
                .bestMatch(totalNumberOfPatterns)
                .flatMap(_.productTypes.lift(argIndex))
                .map(subst)
            case _ => None
          }
        case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor))
          if fun.name == CommonNames.UnapplySeq && fun.parameters.count(!_.isImplicitParameter) == 1 =>

          val subst = expected match {
            case Some(tp) => substitutor.followed(PatternTypeInference.doTypeInference(contextPattern, tp))
            case _        => substitutor
          }

          fun.returnType match {
            case Right(rt) =>
              ScPattern.unapplySeqExtractorMatches(subst(rt), pattern, fun)
                .bestMatch(totalNumberOfPatterns)
                .flatMap { m =>
                  Some(subst(m.productTypes.lift(argIndex).getOrElse {
                    pattern match {
                      case SeqExpectingPattern() => m.sequenceType.tryWrapIntoSeqType
                      case _                     => m.sequenceType
                    }
                  }))
                }

            case _ => None
          }
        case Some(ScalaResolveResult(FakeCompanionClassOrCompanionClass(cl: ScClass), subst: ScSubstitutor))
          if cl.isCase && cl.tooBigForUnapply =>
          val undefSubst = subst.followed(ScSubstitutor(ScThisType(cl)))
          val params: Seq[ScParameter] = cl.parameters
          val types = params.map(_.`type`().getOrAny).map(undefSubst)
          val args =
            if (types.nonEmpty && params.last.isVarArgs) types.dropRight(1) ++ types.last.wrapIntoSeqType
            else                                         types
          args.lift(argIndex)
        case _ => None
      }
    }
  }

  private object SeqExpectingPattern {
    def unapply(e: ScPattern): Boolean = e match {
      case named: ScNamingPattern => named.getLastChild.is[ScSeqWildcardPattern]
      case _: ScSeqWildcardPattern => true
      case _ => false
    }
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
    private[this] val seqFqn = place.scalaSeqFqn

    def unapply(tpe: ScType): Option[ScType] = {
      val baseTpes = Iterator(tpe) ++ BaseTypes.iterator(tpe)
      baseTpes.collectFirst {
        case ParameterizedType(ExtractClass(cls), args)
          if args.length == 1 && cls.qualifiedName == seqFqn => args.head
      }
    }
  }

  private[this] def extractedType(returnTpe: ScType, place: PsiElement): Option[ScType] =
    returnTpe match {
      case ParameterizedType(ExtractClass(cls), Seq(arg))
          if cls.qualifiedName == "scala.Option" || cls.qualifiedName == "scala.Some" =>
        arg.toOption
      case other =>
        for {
          _         <- findMember("isEmpty", other, place)
          extracted <- findMember("get", other, place)
        } yield extracted
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

  /**
   * Helps avoid flattening TupleTypes in cases such as:
   * {{{
   *  case class Foo(f: (String, String))
   *
   *  foo match {
   *    case Foo(t) => ??? // One pattern expected, not two
   *  }
   * }}}
   */
  private[this] def isOneArgSyntheticUnapply(fun: ScFunction): Boolean =
    fun.isSynthetic &&
      fun.syntheticCaseClass
        .flatMap(_.constructor)
        .exists(_.effectiveFirstParameterSection.length == 1)

  private def isProduct(tpe: ScType): Boolean = {
    val productFqn = "scala.Product"
    val baseTpes = Iterator(tpe) ++ BaseTypes.iterator(tpe)
    baseTpes.exists {
      case ExtractClass(cls) if cls.qualifiedName == productFqn => true
      case _                                                    => false
    }
  }


  sealed abstract class ExtractorMatch {
    def isApplicable(subPatternCount: Int): Boolean
    def productTypes: Seq[ScType]
    def sequenceTypeOption: Option[ScType]
    def isEmpty: Boolean
  }

  object ExtractorMatch {
    sealed case class Unapply(override val productTypes: Seq[ScType]) extends ExtractorMatch {
      def applicableSubPatternCount: Int = productTypes.length

      override def isApplicable(subPatternCount: Int): Boolean = subPatternCount == applicableSubPatternCount
      override def isEmpty: Boolean = productTypes.isEmpty
      override def sequenceTypeOption: None.type = None
    }

    // for types like (String, Boolean, Seq[Int])
    // for types like Seq[Int], productTypes is empty
    sealed case class UnapplySeq(override val productTypes: Seq[ScType], sequenceType: ScType) extends ExtractorMatch {
      def minSubPatternCount: Int = productTypes.length

      override def isApplicable(subPatternCount: Int): Boolean = subPatternCount >= minSubPatternCount
      override def isEmpty: Boolean = false
      override def sequenceTypeOption: Some[ScType] = Some(sequenceType)
    }

    /**
     * Returns the best extractor match for the given pattern count.
     * "best match" is defined as:
     * 1. if there are applicable matches, the one with the highest precedence is returned
     * 2. otherwise the match with the least missing patterns is returned
     * 3. if there are only matches with too few patterns, the one with the most patterns is returned
     */
    def bestMatch[T <: ExtractorMatch](matches: LazyList[T], patternCount: Int): Option[T] = {
      def matchWithLeastMissingPatterns = matches.iterator
        .filter(_.productTypes.length > patternCount)
        .minByOption(_.productTypes.length)

      def matchWithMostPatterns = matches
        .maxByOption(_.productTypes.length)

      matches.find(_.isApplicable(patternCount))
        .orElse(matchWithLeastMissingPatterns)
        .orElse(matchWithMostPatterns)
    }

    implicit class LazyListExt[T <: ExtractorMatch](private val list: LazyList[T]) extends AnyVal {
      def bestMatch(patternCount: Int): Option[T] = ExtractorMatch.bestMatch(list, patternCount)
    }
  }

  /**
   * Returns the types of subpatterns for all applicable matching methods in scala 3 in the order of precedence.
   * See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
   */
  private[this] def scala3UnapplyExtractorMatches(tpe: ScType, place: PsiElement): LazyList[ExtractorMatch.Unapply] = {
    def withAutoTupling(types: Seq[ScType]): Option[Seq[ScType]] =
      types match {
        case Seq(TupleType(comps)) => Some(comps)
        case _                     => None
      }

    /*
     * Scala 3 boolean match
     */
    if (tpe.widenIfLiteral.isBoolean) {
      // if tpe is a boolean then it cannot be any of the other matches
      // so we don't even need to try them and can just return
      return LazyList(ExtractorMatch.Unapply(Seq.empty))
    }

    /*
     * Scala 3 product match for types that implement scala.Product
     * and have _1..._N methods
     */
    lazy val productMatch = tpe match {
      case TupleType(comps)    => Some(comps)
      case _ if isProduct(tpe) => Some(extractPossibleProductParts(tpe, place)).filter(_.nonEmpty)
      case _                   => None
    }

    /*
     * Scala 3 single match for types that conform to
     * {
     *   def isEmpty: Boolean
     *   def get: S
     * }
     * // S is the single match
     */
    lazy val singleMatch = extractedType(tpe, place)

    /*
     * # Named based match
     * If there was a single match and S has _1, _2, ... _N methods (N >= 2),
     * then these can be matched as well
     */
    def nameBasedMatch: Option[Seq[ScType]] = singleMatch
      .map {
        case TupleType(comps) => comps
        case tpe => extractPossibleProductParts(tpe, place)
      }
      .filter(_.length >= 2)

    (
      productMatch #::
      productMatch.flatMap(withAutoTupling) #::
      singleMatch.map(Seq(_)) #::
      nameBasedMatch #::
      LazyList.empty
    ).flatten.map(ExtractorMatch.Unapply)
  }

  private def scala2UnapplyExtractorMatches(tpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.Unapply] = {
    /*
     * Scala 2 boolean match
     */
    if (tpe.widenIfLiteral.isBoolean) {
      // if tpe is a boolean then it cannot be any of the other matches
      // so we don't even need to try them and can just return
      return LazyList(ExtractorMatch.Unapply(Seq.empty))
    }

    val extractorType = extractedType(tpe, place)

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

        val comps = extractorType match {
          case Some(extractorType) if hasOnlyOneParameter => Seq(extractorType)
          case Some(TupleType(comps)) => comps
          case _ =>
            // Hmm... something went wrong...
            Seq.empty
        }
        return LazyList(ExtractorMatch.Unapply(comps))
      case _ => ()
    }

    extractorType match {
      case None => LazyList.empty
      case Some(extractorType) =>
        /*
         * First the extractedType can be a matched itself
         */
        val extractorMatch = ExtractorMatch.Unapply(Seq(extractorType))

        /*
         * Next check if it has _1, _2, ... _N methods (N >= 2)
         */
        def nameBased = {
          val byNameExtractor = ByNameExtractor(place)
          extractorType match {
            case TupleType(comps)       => LazyList(ExtractorMatch.Unapply(comps))
            case byNameExtractor(comps) => LazyList(ExtractorMatch.Unapply(comps))
            case _                      => LazyList.empty
          }
        }

        extractorMatch #:: nameBased
    }
  }


  def unapplyExtractorMatches(returnTpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.Unapply] = {
    if (place.isInScala3File) scala3UnapplyExtractorMatches(returnTpe, place)
    else scala2UnapplyExtractorMatches(returnTpe, place, fun)
  }

  /**
   * Returns the types of subpatterns for all applicable matching methods in scala 3 in the order of precedence.
   * See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
   */
  private[this] def scala3UnapplySeqMatches(tpe: ScType, place: PsiElement): LazyList[ExtractorMatch.UnapplySeq] = {
    // v is the V from https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
    def inner(v: ScType, extract: Boolean): LazyList[ExtractorMatch.UnapplySeq] = {
      /**
       * Scala 3 sequence match for types that conform to sequence type (see [[extractSequenceMatchType]])
       */
      val sequenceMatch = extractSequenceMatchType(v, place).map(ExtractorMatch.UnapplySeq(Seq.empty, _))

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
            .map(ExtractorMatch.UnapplySeq(productComponents.init, _))
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
      def extracted = extractedType(v, place) match {
        case Some(tpe) => inner(tpe, extract = false)
        case None      => LazyList.empty
      }

      (
        sequenceMatch #::
        productSequenceMatch #::
          LazyList.empty
      ).flatten ++
        (if (extract) extracted else LazyList.empty)
    }

    inner(tpe, extract = true)
  }

  private def scala2UnapplySeqMatches(tpe: ScType, place: PsiElement): LazyList[ExtractorMatch.UnapplySeq] =
    extractedType(tpe, place) match {
      case None => LazyList.empty
      case Some(extractorType) =>
        def typesToUnapplySeqMatch(types: Seq[ScType]): LazyList[ExtractorMatch.UnapplySeq] = {
          extractSeqElementType(types.last, place)
            .map(ExtractorMatch.UnapplySeq(types.init, _))
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
            case TupleType(comps)       => typesToUnapplySeqMatch(comps)
            case byNameExtractor(comps) => typesToUnapplySeqMatch(comps)
            case _                      => LazyList.empty
          }
        }

        /*
          * if that didn't work, check if the extracted type itself conforms to Seq[_]
          * example:
          *   def unapplySeq(a: A): Option[Seq[Boolean]]
         */
        def extractorMatch = typesToUnapplySeqMatch(Seq(extractorType))

        memberBased ++ extractorMatch
    }

  def unapplySeqExtractorMatches(returnTpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch.UnapplySeq] = {
    if (place.isInScala3File) scala3UnapplySeqMatches(returnTpe, place)
    else scala2UnapplySeqMatches(returnTpe, place)
  }

  /*
   * Returns all possible extractor matches for `returnTpe` returned by `fun` in `place`.
   * The matches are ordered by precedence (highest precedence first).
   */
  def extractorMatches(returnTpe: ScType, place: PsiElement, fun: ScFunction): LazyList[ExtractorMatch] = {
    if (fun.name == CommonNames.Unapply) unapplyExtractorMatches(returnTpe, place, fun)
    else                                 unapplySeqExtractorMatches(returnTpe, place, fun)
  }

  def isQuasiquote(fun: ScFunction): Boolean = {
    val fqnO = Option(fun.containingClass).flatMap(_.qualifiedName.toOption)
    fqnO.exists(fqn => fqn.contains('.') && fqn.substring(0, fqn.lastIndexOf('.')) == "scala.reflect.api.Quasiquotes.Quasiquote")
  }
}
