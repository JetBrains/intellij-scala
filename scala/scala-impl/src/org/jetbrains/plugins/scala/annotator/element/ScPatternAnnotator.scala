package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.AddCaseToGeneratorQuickfix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.bm4.Implicit0Pattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ExtractorMatch
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern.ExtractorTarget
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScInfixTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScExpression, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariableDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.types.ComparingUtil.{isNeverSubClass, isNeverSubType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, AnyVal, NamedTupleType, Nothing, Null, TupleType, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScAbstractType, ScParameterizedType, ScType, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

object ScPatternAnnotator extends ElementAnnotator[ScPattern] {

  override def annotate(pattern: ScPattern, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    pattern match {
      case ScTypedPattern(ScInfixTypeElement(_, op, _)) =>
        holder.createErrorAnnotation(op, ScalaBundle.message("cannot.have.infix.type.directly.in.typed.pattern.try.to..."))
      case _ =>
    }

    if (typeAware) {
      checkPattern(pattern)
    }
  }

  private def checkPattern(pattern: ScPattern)
                          (implicit holder: ScalaAnnotationHolder): Unit = {
    for {
      pType <- patternType(pattern)
      eType <- pattern.expectedType
    } {
      checkPatternType(pType, eType, pattern)
    }
  }

  /**
    * Logic in this method is mimicked from compiler sources:
    * [[scala.tools.nsc.typechecker.Infer.Inferencer]] and [[scala.tools.nsc.typechecker.Checkable]]
    *
    */
  private def checkPatternType(_patType: ScType, exprType: ScType, pattern: ScPattern)
                              (implicit holder: ScalaAnnotationHolder): Unit = {
    import pattern.projectContext
    implicit val tpc: TypePresentationContext = TypePresentationContext(pattern)
    implicit val context: Context = Context(pattern)

    val dealiased = ScalaType.expandAliases(exprType).getOrElse(exprType)
    val exTp      = widen(dealiased)
    val patType   = _patType.removeAliasDefinitions()

    def freeTypeParams = freeTypeParamsOfTerms(dealiased)

    def exTpMatchesPattp = matchesPattern(exTp, widen(patType))

    def hasNoFreeTypeVariables(pattern: ScPattern): Boolean =
      pattern.typeVariables.isEmpty && freeTypeParams.isEmpty

    lazy val patternTypeAsTuple =
      ExtractorMatch.ByNameExtractor(pattern).unapply(patType).map {
        productElements =>
          TupleType(productElements, context = pattern)
      }

    lazy val neverMatches =
      !matchesPattern(exTp, patternTypeAsTuple.getOrElse(patType)) &&
        isNeverSubType(abstraction(patType), exTp) &&
        hasNoFreeTypeVariables(pattern) &&
        !(patType.isNumericType && exTp.isNumericType)

    def isEliminatedByErasure = (exprType.extractClass, patType.extractClass) match {
      case (Some(cl1), Some(cl2)) if pattern.is[ScTypedPattern] => !isNeverSubClass(cl1, cl2)
      case _ => false
    }

    def isTupleAsInfixArgList: Boolean =
      pattern.asOptionOf[ScTuplePattern].exists(_.infixPatternOfWhichThisIsTheArgPatternList.isDefined)

    object StableIdResolvesToVar {

      def unapply(stable: ScStableReferencePattern): Boolean = stable match {
        case ScStableReferencePattern(ResolvesTo(inNameContext(context))) =>
          context match {
            case param: ScClassParameter => param.isVar
            case _: ScVariable => true
            case _ => false
          }
        case _ => false
      }
    }

    pattern match {
      case _: ScTypedPatternLike if Seq(Nothing, Null, AnyVal) contains patType =>
        val message = ScalaBundle.message("type.cannot.be.used.in.type.pattern", patType.presentableText)
        holder.createErrorAnnotation(pattern, message)
      case _: ScTypedPatternLike if exTp.isFinalType && freeTypeParams.isEmpty && !exTpMatchesPattp =>
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val message = ScalaBundle.message("scrutinee.incompatible.pattern.type", patTypeText, exprTypeText)
        holder.createErrorAnnotation(pattern, message)
      case ScTypedPattern(typeElem @ ScCompoundTypeElement(_, Some(_))) =>
        val message = ScalaBundle.message("pattern.on.refinement.unchecked")
        holder.createWarningAnnotation(typeElem, message)
      case _: ScExtractorPattern if neverMatches && patType.isFinalType =>
        val message = ScalaBundle.message("constructor.cannot.be.instantiated.to.expected.type", patType, exprType)
        holder.createErrorAnnotation(pattern, message)
      case _: ScTuplePattern | _: ScInfixPattern if !isTupleAsInfixArgList && neverMatches =>
        val message = ScalaBundle.message("pattern.type.incompatible.with.expected", patType, exprType)
        holder.createErrorAnnotation(pattern, message)
      case nt: ScNamedTuplePattern =>
        exprType match {
          case NamedTupleType(comps) =>
            val map = NamedTupleType.makeComponentMap(comps)
            val seen = mutable.Set.empty[String]
            nt.components.foreach {
              case comp@ScNamedTuplePatternComponent(name, _) =>
                val nameElement = comp.nameElement.get
                if (!seen.add(name)) {
                  holder.createErrorAnnotation(nameElement, ScalaBundle.message("duplicated.name.extractor.name", name))
                }
                if (!map.contains(name)) {
                  val message = ScalaBundle.message("cannot.extract.name.from.selector.type", name, exprType.presentableText)
                  holder.createErrorAnnotation(nameElement, message)
                }
              case _: ScNamedConstructorArgPattern => // for when the name element was omitted
              case pat =>
                holder.createErrorAnnotation(pat, ScalaBundle.message("cannot.use.positional.pattern.when.named.patterns.are.used"))
            }
          case _ =>
            val message = ScalaBundle.message("type.cannot.be.matched.by.a.named.tuple.pattern", exprType.presentableText)
            holder.createErrorAnnotation(nt, message)
        }
      case _  if patType.isFinalType && neverMatches =>
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val message = ScalaBundle.message("pattern.type.incompatible.with.expected", patTypeText, exprTypeText)
        holder.createErrorAnnotation(pattern, message)
      case _: ScTypedPatternLike | _: ScExtractorPattern if neverMatches =>
        val erasureWarn =
          if (isEliminatedByErasure) ScalaBundle.message("erasure.warning")
          else ""
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val baseMessage = ScalaBundle.message("fruitless.type.test", exprTypeText, patTypeText)
        holder.createWarningAnnotation(pattern, NlsString.force(baseMessage + erasureWarn))
      case StableIdResolvesToVar() =>
        val message = ScalaBundle.message("stable.identifier.required", pattern.getText)
        holder.createErrorAnnotation(pattern, message)
      case Implicit0Pattern(arg) =>
        arg match {
          case ScTypedPattern(_) => () // valid according to better-monadic-for rewriting rules
          case _ => holder.createErrorAnnotation(arg, ScalaBundle.message("better.monadic.for.invalid.pattern"))
        }
      case _: ScInterpolationPattern => //do not check interpolated patterns for number of arguments
      case extractorPattern: ScExtractorPattern => //check number of arguments
        val argPatterns = extractorPattern.argPatterns
        val numPatterns = argPatterns.length
        val argPatternsShape = extractorPattern.argPatternShape

        extractorPattern.targetFor(Some(exprType)) match {
          case Some(target@ExtractorTarget.UnapplyMatches(matches)) if !target.isMacroExtractor =>
              if (matches.isEmpty) {
                target match {
                  case ExtractorTarget.Function.Returning(rt) =>
                    holder.createErrorAnnotation(pattern, ScalaBundle.message("type.is.not.a.valid.result.type.of.an.unapply.method", rt.presentableText))
                  case _ =>
                    // this is either ExtractorTarget.TooBigCaseClass and shouldn't happen at all because there should always be a match for TooBigCaseClass
                    // Or the return type of the function can not be resolved... in that case also don't give an error
                }

              } else if (argPatternsShape.hasNamedArgs) {
                val extractorMatch = matches.find(_.supportsNamedPatterns)
                extractorMatch match {
                  case Some(extractorMatch) =>
                    val namedPatterns = extractorMatch.namedPatternTypes(pattern)
                    val seen = mutable.Set.empty[String]
                    argPatterns.foreach {
                      case pat@ScNamedConstructorArgPattern(name, _) =>
                        val nameElement = pat.nameElement.get
                        if (!seen.add(name)) {
                          holder.createErrorAnnotation(nameElement, ScalaBundle.message("duplicated.name.extractor.name", name))
                        }
                        if (!namedPatterns.contains(name)) {
                          val message = ScalaBundle.message("cannot.extract.name.from.selector.type", name, extractorMatch.selectorType.presentableText)
                          holder.createErrorAnnotation(nameElement, message)
                        }
                      case _: ScNamedConstructorArgPattern => // for when the name element was omitted
                      case pat =>
                        holder.createErrorAnnotation(pat, ScalaBundle.message("cannot.use.positional.pattern.when.named.patterns.are.used"))
                    }
                  case None =>
                    target.selectorType.foreach { unapplyType =>
                      val message = ScalaBundle.message("type.does.not.support.named.patterns", unapplyType.presentableText)
                      holder.createErrorAnnotation(pattern, message)
                    }
                }
              }else if (!matches.hasApplicable(argPatternsShape)) {
                matches.findApplicable(argPatternsShape.copy(seqAtEnd = false)) match {
                  case Some(m) =>
                    val matchedType = m.productTypes.last.presentableText
                    holder.createErrorAnnotation(argPatterns.last, ScalaBundle.message("cannot.match.type.with.sequence.pattern", matchedType))
                  case None =>
                    val expected = matches.map(_.productTypes.length).max
                    val message = ScalaBundle.message("wrong.number.arguments.extractor", numPatterns.toString, expected)
                    holder.createErrorAnnotation(pattern, message)
                }
              }
          case Some(target@ExtractorTarget.UnapplySeqMatches(matches)) if !target.isMacroExtractor =>
            if (matches.isEmpty) {
              target match {
                case ExtractorTarget.Function.Returning(rt) =>
                  holder.createErrorAnnotation(pattern, ScalaBundle.message("type.is.not.a.valid.result.type.of.an.unapplyseq.method", rt.presentableText))
                case _ =>
                  // this is either ExtractorTarget.TooBigCaseClass and shouldn't happen at all because there should always be a match for TooBigCaseClass
                  // Or the return type of the function can not be resolved... in that case also don't give an error
              }
            } else if (!matches.hasApplicable(argPatternsShape)) {
              val expected = matches.map(_.productTypes.length).max
              val message = ScalaBundle.message("wrong.number.arguments.extractor.unapplySeq", numPatterns.toString, expected)
              holder.createErrorAnnotation(pattern, message)
            }
          case _ =>
        }
      case _ =>
    }

    if (pattern.isInScala3File) {
      val nonPattern = pattern.contexts
        .takeWhile(e => !e.is[ScExpression, ScBlockStatement] || e.is[ScValueOrVariableDefinition, ScPattern])
        .find(_.is[ScGenerator, ScValueOrVariableDefinition])

      def isIrrefutable = pattern.isIrrefutableFor(exprType, deep = false)
      def message = ScalaBundle.message("pattern.is.not.irrefutable.for.exprType", pattern.getText, exprType.presentableText)

      nonPattern match {
        case Some(gen: ScGenerator) if gen.caseKeyword.isEmpty && !isIrrefutable =>
          val quickfix = new AddCaseToGeneratorQuickfix(gen)
          holder.createWarningAnnotation(pattern, message, quickfix)
        case Some(v: ScValueOrVariableDefinition) if !v.expr.exists(ScalaPsiUtil.isUncheckedExpr) && !isIrrefutable =>
          holder.createWarningAnnotation(pattern, message)
        case _ =>
      }
    }
  }

  private def widen(scType: ScType): ScType = scType match {
    case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
      scType.tryExtractDesignatorSingleton
    case _ =>
      scType.updateLeaves {
        case ScAbstractType(_, _, upper) => upper
        case tpt: TypeParameterType => tpt.upperType
      }
  }

  private def freeTypeParamsOfTerms(tp: ScType): Seq[ScType] = {
    val builder = ArraySeq.newBuilder[ScType]
    tp.visitRecursively {
      case tp: TypeParameterType => builder += tp
      case _ =>
    }
    builder.result()
  }

  private def abstraction(scType: ScType, visited: Set[TypeParameterType] = Set.empty): ScType = {
    scType.updateLeaves {
      case tp: TypeParameterType =>
        if (visited.contains(tp)) tp
        else ScAbstractType(tp.typeParameter,
          abstraction(tp.lowerType, visited + tp),
          abstraction(tp.upperType, visited + tp)
        )
    }
  }

  // TODO Should be in ScPattern, not in the annotator?
  @tailrec
  def matchesPattern(matching: ScType, matched: ScType)(implicit context: Context): Boolean = {
    matching.weakConforms(matched) || ((matching, matched) match {
      case (arrayType(arg1), arrayType(arg2)) => matchesPattern(arg1, arg2)
      case (_, parameterized: ScParameterizedType) =>
        val newtp = abstraction(parameterized)
        !matched.equiv(newtp) && matching.weakConforms(newtp)
      case _ => false
    })
  }

  // TODO Should be in ScPattern, not in the annotator?
  //computes type of the pattern itself, shouldn't rely on expected type
  def patternType(pattern: ScPattern): Option[ScType] = {
    import pattern.projectContext
    implicit val context: Context = Context(pattern)

    def constrPatternType(patternRef: ScStableCodeReference): Option[ScType] = {
      patternRef.bind() match {
        case Some(srr) =>
          srr.getElement match {
            case fun: ScFunction if fun.parameters.count(!_.isImplicit) == 1 =>
              fun.parametersTypes().headOption
                .map(srr.substitutor)
            case _ => None
          }
        case None => None
      }
    }

    pattern match {
      case c: ScExtractorPattern =>
        constrPatternType(c.ref)
      case tuple: ScTuplePattern =>
        val subPat = tuple.subpatterns
        val subTypes = subPat.flatMap(patternType)
        if (subTypes.size == subPat.size) {
          Some(TupleType(subTypes, context = pattern))
        }
        else None
      case typed: ScTypedPatternLike =>
        typed.typePattern.map(_.typeElement.calcType)
      case naming: ScNamingPattern =>
        patternType(naming.named)
      case parenth: ScParenthesisedPattern =>
        patternType(parenth.innerElement.orNull)
      case null => None
      case _: ScReferencePattern | _: ScWildcardPattern => Some(Any) //these only have expected type
      case _ => pattern.`type`().toOption
    }
  }
}