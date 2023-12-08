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
            case Some(tp) => PatternTypeInference.doTypeInference(contextPattern, tp)
            case _        => substitutor
          }

          fun.returnType match {
            case Right(rt) =>
              val args = ScPattern.unapplySubpatternTypes(subst(rt), pattern, fun, totalNumberOfPatterns)

              if (totalNumberOfPatterns == 1 && args.length > 1) Some(TupleType(args))
              else if (argIndex < args.length)                   Some(subst(args(argIndex)).unpackedType)
              else                                               None
            case _ => None
          }
        case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor))
          if fun.name == CommonNames.UnapplySeq && fun.parameters.count(!_.isImplicitParameter) == 1 =>

          val subst = expected match {
            case Some(tp) => PatternTypeInference.doTypeInference(contextPattern, tp)
            case _        => substitutor
          }

          fun.returnType match {
            case Right(rt) =>
              val subpatternTpes = ScPattern.unapplySubpatternTypes(subst(rt), pattern, fun, totalNumberOfPatterns)

              if (subpatternTpes.isEmpty) None
              else {
                val tpe =
                  if (argIndex < subpatternTpes.length) subpatternTpes(argIndex)
                  else                                  subpatternTpes.last

                pattern match {
                  case SeqExpectingPattern() => subst(tpe).tryWrapIntoSeqType.toOption
                  case _                     => subst(tpe).toOption
                }
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

  def expectedNumberOfExtractorArguments(
    returnType:         ScType,
    place:              PsiElement,
    fun:                ScFunction,
    expectedComponents: Int
  ): Int =
    unapplySubpatternTypes(returnType, place, fun, expectedComponents).size

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
    for {
      _  <- findMember("lengthCompare", tpe, place).orElse(findMember("length", tpe, place))
      _  <- findMember("drop", tpe, place)
      _  <- findMember("toSeq", tpe, place)
      t1 <- findMember("apply", tpe, place)
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
      (fun.syntheticCaseClass match {
        case null  => false
        case clazz => clazz.constructor.exists(_.effectiveFirstParameterSection.length == 1)
      })


  private[this] def scala2ProductElementTypes(returnTpe: ScType, place: PsiElement, fun: ScFunction): Seq[ScType] =
    if (returnTpe.widenIfLiteral.isBoolean) Seq.empty
    else {
      lazy val byNameExtractor = ByNameExtractor(place)
      val extracted            = extractedType(returnTpe, place)

      extracted.map {
        case tpe if !place.isInScala3File && isOneArgSyntheticUnapply(fun) => Seq(tpe)
        case TupleType(comps)                                              => comps
        case byNameExtractor(comps)                                        => comps
        case tpe                                                           => Seq(tpe)
      }.getOrElse(Seq.empty)
    }


  private def isProduct(tpe: ScType): Boolean = {
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
  private[this] def scala3ProductElementTypes(tpe: ScType, place: PsiElement): LazyList[Seq[ScType]] = {
    def withAutoTupling(types: Seq[ScType]): Option[Seq[ScType]] =
      types match {
        case Seq(TupleType(comps)) => Some(comps)
        case _                     => None
      }

    /*
     * Scala 3 product match for types that implement scala.Product
     */
    if (tpe.widenIfLiteral.isBoolean) {
      // if tpe is a boolean then it cannot be any of the other matches
      // so we don't even need to try them and can just return
      return LazyList(Seq.empty)
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
    ).flatten
  }

  sealed abstract class UnapplySeqMatch {
    def minArgPatterns: Int
  }
  object UnapplySeqMatch {
    // for types like Seq[Int]
    final case class Sequence(tpe: ScType) extends UnapplySeqMatch {
      override def minArgPatterns: Int = 0
    }

    // for types like (String, Boolean, Seq[Int])
    final case class ProductSequence(productComponents: Seq[ScType], tpe: ScType) extends UnapplySeqMatch {
      override def minArgPatterns: Int = productComponents.length
    }
  }

  /**
   * Returns the types of subpatterns for all applicable matching methods in scala 3 in the order of precedence.
   * See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
   */
  private[this] def scala3UnapplySeqMatches(tpe: ScType, place: PsiElement): LazyList[UnapplySeqMatch] = {
    // v is the V from https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
    def inner(v: ScType, extract: Boolean): LazyList[UnapplySeqMatch] = {
      /**
       * Scala 3 sequence match for types that conform to sequence type (see [[extractSequenceMatchType]])
       */
      def sequenceMatch = extractSequenceMatchType(v, place).map(UnapplySeqMatch.Sequence)

      /**
       * Scala 3 product sequence match for types that implement Product and have _1..._N methods,
       * where N > 0 and _N conforms to the sequence type (see [[extractSequenceMatchType]])
       */
      def productSequenceMatch = {
        val productComponents = extractPossibleProductParts(v, place)
        productComponents.lastOption
          .flatMap(extractSequenceMatchType(_, place))
          .map(UnapplySeqMatch.ProductSequence(productComponents, _))
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

  private def unapplySubpatternTypesScala3(returnTpe: ScType, place: PsiElement, fun: ScFunction, expectedComponents: Int): Seq[ScType] = {
    if (fun.name == CommonNames.Unapply) {
      val tpes = scala3ProductElementTypes(returnTpe, place)
      tpes
        .find(_.length == expectedComponents)
        .orElse(tpes.headOption) // return types of the highest precedence
        .getOrElse(Seq.empty)
    } else {
      scala3UnapplySeqMatches(returnTpe, place)
        .find(_.minArgPatterns <= expectedNumberOfExtractorArguments(returnTpe, place, fun, -1))
        .map {
          case UnapplySeqMatch.Sequence(tpe) => Seq(tpe)
          case UnapplySeqMatch.ProductSequence(productComponents, tpe) =>
            productComponents ++ Seq(tpe)
        }
        .getOrElse(Seq.empty)
    }
  }

  private def unapplySubpatternTypesScala2(returnTpe: ScType, place: PsiElement, fun: ScFunction): Seq[ScType] = {
    val isUnapplySeq = fun.name == CommonNames.UnapplySeq
    val tpes         = scala2ProductElementTypes(returnTpe, place, fun)

    if (tpes.isEmpty)       Seq.empty
    else if (!isUnapplySeq) tpes
    else
      extractSeqElementType(tpes.last, place).fold(Seq.empty[ScType])(tpes.init :+ _)
  }

  /*
   * Returns the types of the subpatterns when matching `returnTpe`
   * In Scala3's matching semantics the matching method that is applicable depends
   * on the number of receiving subpatterns, here given by `expectedComponents`.
   * If you are in a situation where you don't know the amount of receiving subpatterns,
   * you can just give -1 and receive the types of the subpatterns for the applicable matching method that
   * has the highest precedence.
   *
   * TODO: Ideally this method should not take `expectedComponents` and simply return all applicable matching methods
   *       And it should be split into unapplySeq and unapply
   *       But this would require me to implement this for Scala2 as well, which I don't want to do right now
   */
  def unapplySubpatternTypes(returnTpe: ScType, place: PsiElement, fun: ScFunction, expectedComponents: Int): Seq[ScType] =
    if (place.isInScala3File) unapplySubpatternTypesScala3(returnTpe, place, fun, expectedComponents)
    else                      unapplySubpatternTypesScala2(returnTpe, place, fun)

  def isQuasiquote(fun: ScFunction): Boolean = {
    val fqnO = Option(fun.containingClass).flatMap(_.qualifiedName.toOption)
    fqnO.exists(fqn => fqn.contains('.') && fqn.substring(0, fqn.lastIndexOf('.')) == "scala.reflect.api.Quasiquotes.Quasiquote")
  }
}
