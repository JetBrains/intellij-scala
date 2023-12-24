package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.bm4.Implicit0Pattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScInfixTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.PatternTypeInference
import org.jetbrains.plugins.scala.lang.psi.types.ComparingUtil.{isNeverSubClass, isNeverSubType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, AnyVal, Nothing, Null, TupleType, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScParameterizedType, ScType, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

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
    implicit val ctx: ProjectContext = pattern
    implicit val tpc: TypePresentationContext = TypePresentationContext(pattern)

    val dealiased = ScalaType.expandAliases(exprType).getOrElse(exprType)
    val exTp      = widen(dealiased)
    val patType   = _patType.removeAliasDefinitions()

    def freeTypeParams = freeTypeParamsOfTerms(dealiased)

    def exTpMatchesPattp = matchesPattern(exTp, widen(patType))

    def hasNoFreeTypeVariables(pattern: ScPattern): Boolean =
      pattern.typeVariables.isEmpty && freeTypeParams.isEmpty

    val patternTypeAsTuple =
      ScPattern.ByNameExtractor(pattern).unapply(patType).map {
        productElements =>
          TupleType(productElements)(pattern.elementScope)
      }

    val neverMatches =
      !matchesPattern(exTp, patternTypeAsTuple.getOrElse(patType)) &&
        isNeverSubType(abstraction(patType), exTp) &&
        hasNoFreeTypeVariables(pattern)

    def isEliminatedByErasure = (exprType.extractClass, patType.extractClass) match {
      case (Some(cl1), Some(cl2)) if pattern.is[ScTypedPattern] => !isNeverSubClass(cl1, cl2)
      case _ => false
    }

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
      case _: ScConstructorPattern if neverMatches && patType.isFinalType =>
        val message = ScalaBundle.message("constructor.cannot.be.instantiated.to.expected.type", patType, exprType)
        holder.createErrorAnnotation(pattern, message)
      case _: ScTuplePattern | _: ScInfixPattern if neverMatches =>
        val message = ScalaBundle.message("pattern.type.incompatible.with.expected", patType, exprType)
        holder.createErrorAnnotation(pattern, message)
      case _  if patType.isFinalType && neverMatches =>
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val message = ScalaBundle.message("pattern.type.incompatible.with.expected", patTypeText, exprTypeText)
        holder.createErrorAnnotation(pattern, message)
      case _: ScTypedPatternLike | _: ScConstructorPattern if neverMatches =>
        val erasureWarn =
          if (isEliminatedByErasure) ScalaBundle.message("erasure.warning")
          else ""
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val message = ScalaBundle.message("fruitless.type.test", exprTypeText, patTypeText) + erasureWarn
        holder.createWarningAnnotation(pattern, message)
      case StableIdResolvesToVar() =>
        val message = ScalaBundle.message("stable.identifier.required", pattern.getText)
        holder.createErrorAnnotation(pattern, message)
      case Implicit0Pattern(arg) =>
        arg match {
          case ScTypedPattern(_) => () // valid according to better-monadic-for rewriting rules
          case _ => holder.createErrorAnnotation(arg, ScalaBundle.message("better.monadic.for.invalid.pattern"))
        }
      case _: ScInterpolationPattern => //do not check interpolated patterns for number of arguments
      case _: ScConstructorPattern | _: ScInfixPattern => //check number of arguments
        val (reference, numPatterns) = pattern match {
          case constr: ScConstructorPattern => (Option(constr.ref), constr.args.patterns.length)
          case infix: ScInfixPattern =>
            val numPatterns: Int = infix.rightOption match {
              case Some(_: ScInfixPattern | _: ScConstructorPattern) => 2
              case Some(right) => right.subpatterns match {
                case Seq() => 2
                case s => s.length + 1
              }
              case _ => 1
            }
            (Option(infix.operation), numPatterns)
        }
        reference match {
          case Some(ref) =>
            ref.bind() match {
              case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "unapply" => fun.returnType match {
                case Right(rt) =>
                  val substitutor = PatternTypeInference.doTypeInference(pattern, exprType)
                  val expected = ScPattern.expectedNumberOfExtractorArguments(substitutor(rt), pattern, fun, numPatterns)
                  val tupleCrushingIsPresent = expected > 0 && numPatterns == 1 && !fun.isSynthetic
                  if (expected != numPatterns && !tupleCrushingIsPresent) { //1 always fits if return type is Option[TupleN]
                    val message = ScalaBundle.message("wrong.number.arguments.extractor", numPatterns.toString, expected.toString)
                    holder.createErrorAnnotation(pattern, message)
                  }
                case _ =>
              }
              case Some(ScalaResolveResult(fun: ScFunction, substitutor)) if fun.name == "unapplySeq" => fun.returnType match {
                case Right(rt) =>
                  //subtract 1 because last argument (Seq) may be omitted
                  val expected = ScPattern.expectedNumberOfExtractorArguments(substitutor(rt), pattern, fun, numPatterns) - 1
                  if (expected > numPatterns) {
                    val message = ScalaBundle.message("wrong.number.arguments.extractor.unapplySeq", numPatterns.toString, expected.toString)
                    holder.createErrorAnnotation(pattern, message)
                  }
                case _ =>
              }
              case _ =>
            }
          case _ =>
        }
      case _ =>
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
  def matchesPattern(matching: ScType, matched: ScType): Boolean = {

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

    def constrPatternType(patternRef: ScStableCodeReference): Option[ScType] = {
      patternRef.bind() match {
        case Some(srr) =>
          srr.getElement match {
            case fun: ScFunction if fun.parameters.count(!_.isImplicitParameter) == 1 =>
              fun.parametersTypes.headOption
                .map(srr.substitutor)
            case _ => None
          }
        case None => None
      }
    }

    pattern match {
      case c: ScConstructorPattern =>
        constrPatternType(c.ref)
      case inf: ScInfixPattern =>
        constrPatternType(inf.operation)
      case tuple: ScTuplePattern =>
        val subPat = tuple.subpatterns
        val subTypes = subPat.flatMap(patternType)
        if (subTypes.size == subPat.size) {
          Some(TupleType(subTypes)(pattern.elementScope))
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