package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.FakeCompanionClassOrCompanionClass
import org.jetbrains.plugins.scala.lang.psi.api.ExtractorMatch
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern.{ArgPatternShape, ArgPatternsMapping, ExtractorTarget}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern.{SeqExpectingPattern, isSeqExpectingPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.PatternTypeInference
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.ExpandedExtractorResolveProcessor
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.scalaMeta.QuasiquoteInferUtil

/**
 * Base trait for patterns, which resolve to unapply/unapplySeq methods
 */
trait ScExtractorPattern extends ScPattern {
  def ref: ScStableCodeReference
  final def argPatterns: Seq[ScPattern] = subpatterns
  final def argPatternShape: ArgPatternShape = ArgPatternShape.from(argPatterns)

  final def bindWithScrutinee(scrutineeType: Option[ScType]): Option[ScalaResolveResult] =
    ExpandedExtractorResolveProcessor.resolveActualUnapply(ref, scrutineeType)

  final def targetFor(scrutineeType: Option[ScType]): Option[ExtractorTarget] = {
    bindWithScrutinee(scrutineeType).collect {
      case srr@ScalaResolveResult(fun: ScFunction, _) if fun.name == CommonNames.Unapply && ScPattern.isQuasiquote(fun) =>
        new ExtractorTarget.Quasiquote(fun, srr, scrutineeType, this)
      case srr@ScalaResolveResult(fun: ScFunction, _) if fun.name == CommonNames.Unapply && QuasiquoteInferUtil.isMetaQQ(fun) =>
        new ExtractorTarget.MetaQQ(fun, srr, scrutineeType, this)
      case srr@ScalaResolveResult(fun: ScFunction, _) if fun.name == CommonNames.Unapply && fun.parameters.count(!_.isImplicit) == 1 =>
        new ExtractorTarget.Unapply(fun, srr, scrutineeType, this)
      case srr@ScalaResolveResult(fun: ScFunction, _) if fun.name == CommonNames.UnapplySeq && fun.parameters.count(!_.isImplicit) == 1 =>
        new ExtractorTarget.UnapplySeq(fun, srr, scrutineeType, this)
      case srr@ScalaResolveResult(FakeCompanionClassOrCompanionClass(cl: ScClass), _) if cl.isCase && cl.tooBigForUnapply =>
        new ExtractorTarget.TooBigCaseClass(cl,srr, scrutineeType, this)
    }
  }

  final def argPatternsMapping(scrutineeType: Option[ScType]): Option[ArgPatternsMapping] =
    targetFor(scrutineeType).flatMap(_.argPatternsMapping)
}

object ScExtractorPattern {
  case class ArgPatternShape(totalArgCount: Int, seqAtEnd: Boolean, hasNamedArgs: Boolean) {
    def nonSeqArgCount: Int = totalArgCount - (if (seqAtEnd) 1 else 0)
  }
  object ArgPatternShape {
    def from(subpatterns: Seq[ScPattern]): ArgPatternShape = {
      val len = subpatterns.length
      val seqAtEnd = subpatterns.lastOption.exists(isSeqExpectingPattern)
      val hasNamedArgs = subpatterns.exists(_.is[ScNamedConstructorArgPattern])
      ArgPatternShape(len, seqAtEnd, hasNamedArgs)
    }
  }

  sealed abstract class ExtractorTarget(val resolveResult: ScalaResolveResult, val scrutineeType: Option[ScType], protected val pattern: ScExtractorPattern) {
    def matchedType: Option[ScType]
    def selectorType: Option[ScType]
    def isIrrefutable: Boolean

    final lazy val substitutor: ScSubstitutor = {
      val subst = resolveResult.substitutor
      scrutineeType.fold(subst)(tp => subst.followed(PatternTypeInference.doTypeInference(pattern, tp)))
    }

    def isMacroExtractor: Boolean
    def argPatternsMapping: Option[ArgPatternsMapping]
  }

  object ExtractorTarget {
    sealed abstract class Function(ssr: ScalaResolveResult, sty: Option[ScType], pattern: ScExtractorPattern) extends ExtractorTarget(ssr, sty, pattern) {
      def targetFunction: ScFunction
      final def returnType: Option[ScType] = targetFunction.returnType.map(substitutor).toOption

      final override def selectorType: Option[ScType] = returnType
      final override def matchedType: Option[ScType] =
        targetFunction.parameters.find(!_.isImplicit).flatMap(_.`type`().toOption).map(substitutor)

      final override def isMacroExtractor: Boolean = targetFunction.is[ScMacroDefinition]
    }

    trait WithExtractorMatches extends ExtractorTarget {
      /**
       * The ExtractorMatch that matches the shape of subpatterns
       */
      def extractorMatch: Option[ExtractorMatch] = extractorMatches.flatMap { matches =>
        val shape = pattern.argPatternShape
        matches.findApplicable(shape)
      }

      /**
       * All possible ExtractorMatches without regarding subpatterns
       */
      def extractorMatches: Option[LazyList[ExtractorMatch]]

      final override def isIrrefutable: Boolean =
        scrutineeType.zip(matchedType).forall { case (s, m) => s conforms m } &&
          extractorMatch.exists(_.isIrrefutable(pattern.argPatternShape))
    }

    final class Quasiquote private[ScExtractorPattern](override val targetFunction: ScFunction, ssr: ScalaResolveResult, sty: Option[ScType], p: ScExtractorPattern) extends Function(ssr, sty, p) {
      override def argPatternsMapping: Some[ArgPatternsMapping] = Some(new ArgPatternsMapping.Quasiquote(pattern, targetFunction))
      override def isIrrefutable: false = false
    }

    final class MetaQQ private[ScExtractorPattern](override val targetFunction: ScFunction, ssr: ScalaResolveResult, sty: Option[ScType], p: ScExtractorPattern) extends Function(ssr, sty, p) {
      override def argPatternsMapping: Option[ArgPatternsMapping] = try {
        val interpolationPattern = pattern.asInstanceOf[ScInterpolationPatternImpl]
        val qqPatterns = QuasiquoteInferUtil.getMetaQQPatternTypes(interpolationPattern)
        Some(new ArgPatternsMapping.MetaQQ(pattern.subpatterns.zip(qqPatterns).toMap, targetFunction))
      } catch {
        case _: ArrayIndexOutOfBoundsException => None // workaround for meta parser failure on malformed quasiquotes
      }
      override def isIrrefutable: false = false
    }

    final class Unapply private[ScExtractorPattern](override val targetFunction: ScFunction, ssr: ScalaResolveResult, sty: Option[ScType], p: ScExtractorPattern) extends Function(ssr, sty, p) with WithExtractorMatches {
      override def extractorMatches: Option[LazyList[ExtractorMatch.Unapply]] = returnType.map(ExtractorMatch.unapplyExtractorMatches(_, pattern, targetFunction))
      override def argPatternsMapping: Option[ArgPatternsMapping] = extractorMatches.map(new ArgPatternsMapping.Unapply(_, pattern))
    }

    final class UnapplySeq private[ScExtractorPattern](override val targetFunction: ScFunction,ssr: ScalaResolveResult, sty: Option[ScType], p: ScExtractorPattern) extends Function(ssr, sty, p) with WithExtractorMatches {
      override def extractorMatches: Option[LazyList[ExtractorMatch.UnapplySeq]] = returnType.map(ExtractorMatch.unapplySeqExtractorMatches(_, pattern, targetFunction))
      override def argPatternsMapping: Option[ArgPatternsMapping] = extractorMatches.map(new ArgPatternsMapping.UnapplySeq(_, pattern))
    }

    final class TooBigCaseClass private[ScExtractorPattern](val clazz: ScClass, ssr: ScalaResolveResult, sty: Option[ScType], p: ScExtractorPattern) extends ExtractorTarget(ssr, sty, p) with WithExtractorMatches {
      override def selectorType: Option[ScType] = clazz.`type`().toOption.map(substitutor)
      override def matchedType: Option[ScType] = selectorType

      override lazy val extractorMatch: Some[ExtractorMatch] = {
        implicit val elementScope: ElementScope = pattern.elementScope
        val undefSubst = substitutor.followed(ScSubstitutor(ScThisType(clazz)))
        val params: Seq[ScParameter] = clazz.parameters
        val types = params.map(_.`type`().getOrAny).map(undefSubst)
        def selectorType = this.selectorType.getOrElse(StdTypes.instance.Any)

        Some(
          if (types.nonEmpty && params.last.isVarArgs) {
            ExtractorMatch.UnapplySeq(types.dropRight(1), types.last.tryWrapIntoSeqType, selectorType, irrefutable = true)
          } else {
            ExtractorMatch.Unapply.productWithSelector(types, selectorType, irrefutable = true, p)
          }
        )
      }

      override def extractorMatches: Some[LazyList[ExtractorMatch]] = Some(LazyList(extractorMatch.value))
      override def argPatternsMapping: Some[ArgPatternsMapping] = extractorMatch.value match {
        case unapply: ExtractorMatch.Unapply => Some(new ArgPatternsMapping.Unapply(LazyList(unapply), pattern))
        case unapply: ExtractorMatch.UnapplySeq => Some(new ArgPatternsMapping.UnapplySeq(LazyList(unapply), pattern))
        case _ =>
          throw new AssertionError("extractorMatch should always be Unapply or UnapplySeq")
      }

      override def isMacroExtractor: false = false
    }

    object UnapplyMatches {
      def unapply(target: ExtractorTarget): Option[LazyList[ExtractorMatch.Unapply]] = target match {
        case target: ExtractorTarget.Unapply => target.extractorMatches
        case target: ExtractorTarget.TooBigCaseClass => target.extractorMatch.collect { case e: ExtractorMatch.Unapply =>  LazyList(e) }
        case _ => None
      }
    }

    object UnapplySeqMatches {
      def unapply(target: ExtractorTarget): Option[LazyList[ExtractorMatch.UnapplySeq]] = target match {
        case target: ExtractorTarget.UnapplySeq => target.extractorMatches
        case target: ExtractorTarget.TooBigCaseClass => target.extractorMatch.collect { case e: ExtractorMatch.UnapplySeq =>  LazyList(e) }
        case _ => None
      }
    }

    object Function {
      def unapply(target: ExtractorTarget.Function): Some[ScFunction] = Some(target.targetFunction)

      object Returning {
        def unapply(target: ExtractorTarget.Function): Option[ScType] = target.returnType
      }
    }
  }

  sealed abstract class ArgPatternsMapping {
    def typeOfArg(pattern: ScPattern): Option[ScType]
  }

  object ArgPatternsMapping {
    private[ScExtractorPattern] final class Quasiquote(extractorPattern: ScExtractorPattern, fun: ScFunction) extends ArgPatternsMapping {
      private implicit def projectContext: ProjectContext = fun.projectContext

      private lazy val seqSeqTreeType =
        ScalaPsiElementFactory.createTypeElementFromText("Seq[Seq[scala.reflect.api.Trees#Tree]]", fun).`type`().toOption
      private lazy val seqTreeType =
        ScalaPsiElementFactory.createTypeElementFromText("Seq[scala.reflect.api.Trees#Tree]", fun).`type`().toOption
      private lazy val treeType =
        ScalaPsiElementFactory.createTypeElementFromText("scala.reflect.api.Trees#Tree", fun).`type`().toOption

      override def typeOfArg(pattern: ScPattern): Option[ScType] = {
        if (pattern.getContext.getContext != extractorPattern)
          return None

        def possible(e: PsiElement): Boolean = e.isWhitespaceOrComment || (e.elementType match {
          case ScalaTokenTypes.tINTERPOLATED_STRING => true
          case ScalaTokenTypes.tLBRACE => true
          case _ => false
        })

        // in a string alá q"xyz ..${pattern} xyz" look for the .. or ...
        val nextInterpolatedString =
          pattern.prevSiblings.takeWhile(possible).find(_.elementType == ScalaTokenTypes.tINTERPOLATED_STRING)

        nextInterpolatedString.map(_.getText) match {
          case Some(str) if str.endsWith("...") => seqSeqTreeType
          case Some(str) if str.endsWith("..")  => seqTreeType
          case _ => treeType
        }
      }
    }

    private[ScExtractorPattern] final class MetaQQ(qqPatterns: Map[ScPattern, String], fun: ScFunction) extends ArgPatternsMapping {
      private implicit def projectContext: ProjectContext = fun.projectContext

      private def makeType(clazz: String): Option[ScType] =
        ScalaPsiElementFactory.createTypeElementFromText(clazz, fun).`type`().toOption

      override def typeOfArg(pattern: ScPattern): Option[ScType] = qqPatterns.get(pattern).flatMap(makeType)
    }

    private[ScExtractorPattern] final class Unapply(matches: LazyList[ExtractorMatch.Unapply], extractorPattern: ScExtractorPattern) extends ArgPatternsMapping {
      private lazy val argPatterns = extractorPattern.argPatterns

      private lazy val forIndexed =
        matches.bestMatch(ArgPatternShape(argPatterns.length, seqAtEnd = false, hasNamedArgs = false))
      private lazy val forNamed =
        matches.find(_.supportsNamedPatterns)

      override def typeOfArg(pattern: ScPattern): Option[ScType] =
        pattern match {
          case arg: ScNamedConstructorArgPattern =>
            forNamed.flatMap { forNamed =>
              forNamed.namedPatternTypes(pattern).get(arg.name).flatten
            }
          case _ =>
            forIndexed.flatMap(_.productTypes.lift(argPatterns.indexOf(pattern)))
        }
    }

    private[ScExtractorPattern] final class UnapplySeq(matches: LazyList[ExtractorMatch.UnapplySeq], pattern: ScExtractorPattern) extends ArgPatternsMapping {
      private implicit def elementScope: ElementScope = pattern.elementScope

      private lazy val argPatterns = pattern.argPatterns
      private lazy val forIndex = matches
        .bestMatch(ArgPatternShape.from(argPatterns))

      override def typeOfArg(pattern: ScPattern): Option[ScType] =
        forIndex.map { forIndex =>
          val i = argPatterns.indexOf(pattern)
          forIndex.productTypes.lift(i).getOrElse {
            pattern match {
              case SeqExpectingPattern() => forIndex.sequenceType.tryWrapIntoSeqType
              case _ => forIndex.sequenceType
            }
          }
        }
    }
  }
}