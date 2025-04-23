package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.AddCaseToGeneratorQuickfix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.bm4.Implicit0Pattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScInfixTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenerator
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition, ScValueOrVariableDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.PatternTypeInference
import org.jetbrains.plugins.scala.lang.psi.types.ComparingUtil.{isNeverSubClass, isNeverSubType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, AnyVal, NamedTupleType, Nothing, Null, TupleType, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScParameterizedType, ScType, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext
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
    implicit val ctx: ProjectContext = pattern
    implicit val tpc: TypePresentationContext = TypePresentationContext(pattern)

    val dealiased = ScalaType.expandAliases(exprType).getOrElse(exprType)
    val exTp      = widen(dealiased)
    val patType   = _patType.removeAliasDefinitions()

    def freeTypeParams = freeTypeParamsOfTerms(dealiased)

    def exTpMatchesPattp = matchesPattern(exTp, widen(patType))

    def hasNoFreeTypeVariables(pattern: ScPattern): Boolean =
      pattern.typeVariables.isEmpty && freeTypeParams.isEmpty

    lazy val patternTypeAsTuple =
      ScPattern.ByNameExtractor(pattern).unapply(patType).map {
        productElements =>
          TupleType(productElements, context = pattern)
      }

    lazy val neverMatches =
      !matchesPattern(exTp, patternTypeAsTuple.getOrElse(patType)) &&
        isNeverSubType(abstraction(patType), exTp) &&
        hasNoFreeTypeVariables(pattern)

    lazy val isIrrefutable = pattern.isIrrefutableFor(Some(exprType))

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
        }
      case _  if patType.isFinalType && neverMatches =>
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val message = ScalaBundle.message("pattern.type.incompatible.with.expected", patTypeText, exprTypeText)
        holder.createErrorAnnotation(pattern, message)
      case _: ScTypedPatternLike | _: ScConstructorPattern if neverMatches =>
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
      case _: ScConstructorPattern | _: ScInfixPattern => //check number of arguments
        val (reference, numPatterns, constructorArgPatterns) = pattern match {
          case constr: ScConstructorPattern =>
            val patterns = constr.subpatterns
            (Option(constr.ref), patterns.length, patterns)
          case infix: ScInfixPattern =>
            val numPatterns: Int = infix.rightOption match {
              case Some(_: ScInfixPattern | _: ScConstructorPattern) => 2
              case Some(right) => right.subpatterns match {
                case Seq() => 2
                case s => s.length + 1
              }
              case _ => 1
            }
            (Option(infix.operation), numPatterns, Seq.empty)
        }
        reference match {
          case Some(ref) =>
            ref.bind() match {
              case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "unapply" && !fun.is[ScMacroDefinition] => fun.returnType match {
                case Right(rt) =>
                  val substitutor = PatternTypeInference.doTypeInference(pattern, exprType)
                  val unapplyType = substitutor(rt)
                  val matches = ScPattern.unapplyExtractorMatches(unapplyType, pattern, fun)
                  val hasNamedArgs = constructorArgPatterns.exists(_.is[ScNamedConstructorArgPattern])

                  if (matches.isEmpty) {
                    holder.createErrorAnnotation(pattern, ScalaBundle.message("type.is.not.a.valid.result.type.of.an.unapply.method", unapplyType.presentableText))
                  } else if (hasNamedArgs) {
                    val extractorMatch = matches.find(_.supportsNamedPatterns)
                    extractorMatch match {
                      case Some(extractorMatch) =>
                        val namedPatterns = extractorMatch.namedPatternTypes(pattern)
                        val seen = mutable.Set.empty[String]
                        constructorArgPatterns.foreach {
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
                        val message = ScalaBundle.message("type.does.not.support.named.patterns", unapplyType.presentableText)
                        holder.createErrorAnnotation(pattern, message)
                    }
                  } else if (!matches.exists(_.isApplicable(numPatterns))) {
                    val expected = matches.map(_.productTypes.length).max
                    val message = ScalaBundle.message("wrong.number.arguments.extractor", numPatterns.toString, expected)
                    holder.createErrorAnnotation(pattern, message)
                  }
                case _ =>
              }
              case Some(ScalaResolveResult(fun: ScFunction, substitutor)) if fun.name == "unapplySeq" => fun.returnType match {
                case Right(rt) =>
                  //subtract 1 because last argument (Seq) may be omitted
                  val unapplyType = substitutor(rt)
                  val matches = ScPattern.unapplySeqExtractorMatches(unapplyType, pattern, fun)
                  if (!matches.exists(_.isApplicable(numPatterns)) && !fun.is[ScMacroDefinition]) {
                    if (matches.isEmpty) {
                      holder.createErrorAnnotation(pattern, ScalaBundle.message("type.is.not.a.valid.result.type.of.an.unapplyseq.method", unapplyType.presentableText))
                    } else {
                      val expected = matches.map(_.productTypes.length).max
                      val message = ScalaBundle.message("wrong.number.arguments.extractor.unapplySeq", numPatterns.toString, expected)
                      holder.createErrorAnnotation(pattern, message)
                    }
                  }
                case _ =>
              }
              case _ =>
            }
          case _ =>
        }
      case Parent(gen: ScGenerator) if
        gen.caseKeyword.isEmpty &&
          pattern.isInScala3Module &&
          !isIrrefutable =>
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val message = ScalaBundle.message("pattern.type.is.more.specialized.than.the.expr", patTypeText, exprTypeText)
        val quickfix = new AddCaseToGeneratorQuickfix(gen)
        if (pattern.features.`Scala 3 Irrefutable Patterns`)
          holder.createErrorAnnotation(pattern, message, quickfix)
        else
          holder.createWarningAnnotation(pattern, message, quickfix)

      case Parent(Parent(v: ScValueOrVariableDefinition)) if !v.expr.exists(ScalaPsiUtil.isUncheckedExpr) && pattern.isInScala3Module && !isIrrefutable =>
        val (exprTypeText, patTypeText) = TypePresentation.different(exprType, patType)
        val message = ScalaBundle.message("pattern.type.is.more.specialized.than.the.expr", patTypeText, exprTypeText)
        if (pattern.features.`Scala 3 Irrefutable Patterns`)
          holder.createErrorAnnotation(pattern, message)
        else
          holder.createWarningAnnotation(pattern, message)

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
            case fun: ScFunction if fun.parameters.count(!_.isImplicit) == 1 =>
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