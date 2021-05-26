package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.FakeCompanionClassOrCompanionClass
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompletionProcessor, ExpandedExtractorResolveProcessor}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import scala.annotation.tailrec
import scala.meta.intellij.QuasiquoteInferUtil

/**
 * @author Alexander Podkhalyuzin
 */

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

    @CachedInUserData(pattern, BlockModificationTracker(pattern))
    def expectedType: Option[ScType] = {
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
              expectedTypeForExtractorArg(constr.ref, thisIndex, constr.expectedType, argList.patterns.length)
            case _ => None
          }
        case composite: ScCompositePattern => composite.expectedType
        case infix: ScInfixPattern =>
          val i =
            if (infix.left == pattern) 0
            else if (pattern.is[ScTuplePattern]) return None //pattern is handled elsewhere in pattern function
            else 1
          expectedTypeForExtractorArg(infix.operation, i, infix.expectedType, 2)
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

                  return expectedTypeForExtractorArg(infix.operation, i + 1, infix.expectedType, patternLength)
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
        case named: ScNamingPattern => named.expectedType
        case _: ScGenerator =>
          pattern.analogInDesugaredForExpr flatMap { _.expectedType }
        case forBinding: ScForBinding =>
          forBinding.expr.flatMap { _.`type`().toOption }
        case _ => None
      }
    }

    private def expectedTypeForExtractorArg(ref: ScStableCodeReference,
                                            argIndex: Int,
                                            expected: Option[ScType],
                                            totalNumberOfPatterns: Int): Option[ScType] = {
      val bind: Option[ScalaResolveResult] = ref.bind() match {
        case Some(ScalaResolveResult(_: ScBindingPattern | _: ScParameter, _)) =>
          val resolve = ref match {
            case refImpl: ScStableCodeReferenceImpl =>
              refImpl.doResolve(new ExpandedExtractorResolveProcessor(ref, ref.refName, ref.getKinds(incomplete = false), ref.getContext match {
                case inf: ScInfixPattern => inf.expectedType
                case constr: ScConstructorPattern => constr.expectedType
                case _ => None
              }))
          }
          resolve match {
            case Array(r) => Some(r)
            case _ => None
          }
        case m => m
      }

      def calculateSubstitutor(`type`: ScType, functionType: ScType,
                               substitutor: ScSubstitutor): ScSubstitutor = {
        val tp = `type` match {
          case ScExistentialType(quantified, _) => quantified
          case _ => `type`
        }

        val substitutedFunctionType = substitutor(functionType)

        tp.conformanceSubstitutor(substitutedFunctionType).orElse {
          substitutedFunctionType.conformanceSubstitutor(tp)
        }.fold(substitutor) {
          _.followed(substitutor)
        }
      }

      bind match {
        case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "unapply" && ScPattern.isQuasiquote(fun) =>
          val tpe = pattern.getContext.getContext match {
            case _: ScInterpolationPattern =>
              val parts = pattern.getParent.asInstanceOf[ScalaPsiElement]
                .findChildrenByType(ScalaTokenTypes.tINTERPOLATED_STRING)
                .map(_.getText)
              if (argIndex < parts.length && parts(argIndex).endsWith("..."))
                ScalaPsiElementFactory.createTypeElementFromText("Seq[Seq[scala.reflect.api.Trees#Tree]]")
              if (argIndex < parts.length && parts(argIndex).endsWith(".."))
                ScalaPsiElementFactory.createTypeElementFromText("Seq[scala.reflect.api.Trees#Tree]")
              else
                ScalaPsiElementFactory.createTypeElementFromText("scala.reflect.api.Trees#Tree")
          }
          tpe.`type`().toOption
        case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "unapply" && QuasiquoteInferUtil.isMetaQQ(fun) =>
          try {
            val interpolationPattern = pattern.getParent.getParent.asInstanceOf[ScInterpolationPatternImpl]
            val patterns = QuasiquoteInferUtil.getMetaQQPatternTypes(interpolationPattern)
            if (argIndex < patterns.size) {
              val clazz = patterns(argIndex)
              val tpe = ScalaPsiElementFactory.createTypeElementFromText(clazz)
              tpe.`type`().toOption
            } else { None }
          } catch {
            case _: ArrayIndexOutOfBoundsException => None // workaround for meta parser failure on malformed quasiquotes
          }
        case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor))
          if fun.name == CommonNames.Unapply && fun.parameters.count(!_.isImplicitParameter) == 1 =>
          val funTypeParams: Seq[ScTypeParam] = fun.typeParameters

          val subst =
            if (funTypeParams.isEmpty) substitutor
            else {
              val undefSubst = ScSubstitutor.bind(funTypeParams)(UndefinedType(_))
              val clazz = ScalaPsiUtil.getContextOfType(pattern, true, classOf[ScTemplateDefinition])
              val withThisType = clazz match {
                case clazz: ScTemplateDefinition =>
                  undefSubst.followed(ScSubstitutor(ScThisType(clazz)))
                case _ => undefSubst
              }
              val firstParameterType = fun.parameters.head.`type`() match {
                case Right(tp) => tp
                case _ => return None
              }
              val funType = withThisType(firstParameterType)
              expected match {
                case Some(tp) => calculateSubstitutor(tp, funType, substitutor)
                case _ => substitutor
              }
            }
          fun.returnType match {
            case Right(rt) =>
              def updateRes(tp: ScType): ScType = {
                tp.recursiveVarianceUpdate() {
                  case (tp: TypeParameterType, variance: Variance) if funTypeParams.contains(tp.psiTypeParameter) =>
                    val result =
                      if (variance == Contravariant) substitutor(tp.lowerType)
                      else substitutor(tp.upperType)
                    ReplaceWith(result)
                  case (_, _) => ProcessSubtypes
                }
              }

              val args = ScPattern.unapplySubpatternTypes(subst(rt), pattern, fun)
              if (totalNumberOfPatterns == 1 && args.length > 1) Some(TupleType(args))
              else if (argIndex < args.length) Some(updateRes(subst(args(argIndex)).unpackedType))
              else None
            case _ => None
          }
        case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor))
          if fun.name == CommonNames.UnapplySeq && fun.parameters.count(!_.isImplicitParameter) == 1 =>
          val typeParameters = fun.typeParameters
          val subst =
            if (typeParameters.isEmpty) substitutor
            else {
              val undefSubst = substitutor followed ScSubstitutor.bind(typeParameters)(UndefinedType(_))
              val firstParameterRetTp = fun.parameters.head.`type`() match {
                case Right(tp) => tp
                case _         => return None
              }
              val funType = undefSubst(firstParameterRetTp)
              expected match {
                case Some(tp) => calculateSubstitutor(tp, funType, substitutor)
                case _        => substitutor
              }
            }
          fun.returnType match {
            case Right(rt) =>
              val subpatternTpes = ScPattern.unapplySubpatternTypes(subst(rt), pattern, fun)

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
      case named: ScNamingPattern => named.getLastChild.is[ScSeqWildcard]
      case _: ScSeqWildcard => true
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
    returnType: ScType,
    place:      PsiElement,
    fun:        ScFunction
  ): Int =
    unapplySubpatternTypes(returnType, place, fun).size

  private[this] case class ByNameExtractor(place: PsiElement) {
    def unapply(tpe: ScType): Option[Seq[ScType]] = {
      val selectors = extractPossibleProductParts(tpe, place)
      if (selectors.length >= 2) Option(selectors)
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
   * Helps aboid flattening TupleTypes in cases such as:
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

  private[this] def productElementTypes(returnTpe: ScType, place: PsiElement, fun: ScFunction): Seq[ScType] =
    if (returnTpe.isBoolean) Seq.empty
    else {
      lazy val byNameExtractor = ByNameExtractor(place)
      val extracted            = extractedType(returnTpe, place)

      extracted.map {
        case tpe if isOneArgSyntheticUnapply(fun) => Seq(tpe)
        case TupleType(comps)                     => comps
        case byNameExtractor(comps)               => comps
        case tpe                                  => Seq(tpe)
      }.getOrElse(Seq.empty)
    }

  def unapplySubpatternTypes(returnTpe: ScType, place: PsiElement, fun: ScFunction): Seq[ScType] = {
    val isUnapplySeq = fun.name == CommonNames.UnapplySeq
    val tpes         = productElementTypes(returnTpe, place, fun)

    if (tpes.isEmpty)       Seq.empty
    else if (!isUnapplySeq) tpes
    else
      extractSeqElementType(tpes.last, place).fold(Seq.empty[ScType])(tpes.init :+ _)
  }

  def isQuasiquote(fun: ScFunction): Boolean = {
    val fqnO = Option(fun.containingClass).flatMap(_.qualifiedName.toOption)
    fqnO.exists(fqn => fqn.contains('.') && fqn.substring(0, fqn.lastIndexOf('.')) == "scala.reflect.api.Quasiquotes.Quasiquote")
  }
}
