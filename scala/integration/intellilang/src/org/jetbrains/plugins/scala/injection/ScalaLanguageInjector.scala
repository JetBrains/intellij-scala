package org.jetbrains.plugins.scala
package injection

import java.{util => ju}

import com.intellij.lang.Language
import com.intellij.lang.injection.{MultiHostInjector, MultiHostRegistrar}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.{Key, TextRange, Trinity}
import com.intellij.psi._
import org.apache.commons.lang3.StringUtils
import org.intellij.plugins.intelliLang.Configuration
import org.intellij.plugins.intelliLang.inject._
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.readAttribute
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPatternPrefix
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.collection.{immutable, mutable}

final class ScalaLanguageInjector extends MultiHostInjector {

  import ScalaLanguageInjector._

  lazy val myInjectionConfiguration: Configuration = Configuration.getInstance()

  override def elementsToInjectIn: ju.List[_ <: Class[_ <: PsiElement]] = ElementsToInjectIn

  // TODO: rethink, add caching
  //  this adds significant typing performance overhead if file contains many injected literals
  @Measure
  override def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement): Unit =  {
    val support = LanguageInjectionSupport.EP_NAME.findExtension(classOf[ScalaLanguageInjectionSupport])
    if (support == null)
      return

    val literals: Seq[StringLiteral] = literalsOf(host)
    if (literals.isEmpty)
      return

    implicit val s: ScalaLanguageInjectionSupport = support
    implicit val r: MultiHostRegistrar = registrar

    if (injectUsingIntention(host, literals))
      return
    if (injectUsingComment(host, literals))
      return

    implicit val projectSettings: ScalaProjectSettings = ScalaProjectSettings.getInstance(host.getProject)
    if (injectInInterpolation(host, literals, projectSettings.getIntInjectionMapping))
      return

    if (projectSettings.isDisableLangInjection)
      return

    if (injectUsingAnnotation(host, literals))
      return

    if (injectUsingPatterns(host, literals, myInjectionConfiguration.getInjections(support.getId)))
      return

    // final expression uses return for easy debugging
  }

  private def isDfaEnabled: Boolean =
    Configuration.getInstance.getAdvancedConfiguration.getDfaOption != Configuration.DfaOption.OFF

  /**
   * @return 1) `host` itself - if host is string literal that is not inside string concatenation <br>
   *         2) string concatenation operands if `host` is a top level concatenation expression <br>
   *         3) empty collection otherwise
   */
  private def literalsOf(host: PsiElement): Seq[StringLiteral] = host.getParent match {
    case ScInfixExpr(_, ElementText("+"), _) =>
      // if string literal is inside concatenations skip it
      // we would like to process top-level expressions only
      Seq.empty
    case _ =>
      val expressions = host.depthFirst {
        case expression: ScExpression => !expression.getParent.isInstanceOf[ScInterpolatedStringLiteral]
        case _ => true
      }.toList.filter(_.isInstanceOf[ScExpression])

      val suitable = expressions.forall {
        case l: ScLiteral if l.isString => true
        case _: ScInterpolatedPatternPrefix |
             _: ScInfixExpr => true
        case r: ScReferenceExpression if r.textMatches("+") => true
        case expression => expression.getParent.isInstanceOf[ScInterpolatedStringLiteral]
      }

      if (suitable) {
        expressions.filter {
          case literal: StringLiteral => literal.isString
          case _ => false
        }.map {
          _.asInstanceOf[StringLiteral]
        }
      } else {
        Seq.empty
      }
  }

  @Measure
  private def injectInInterpolation(host: PsiElement, literals: Seq[ScLiteral],
                                    mapping: ju.Map[String, String])
                                   (implicit support: ScalaLanguageInjectionSupport,
                                    registrar: MultiHostRegistrar): Boolean =
    literals.filterByType[ScInterpolatedStringLiteral with PsiLanguageInjectionHost] match {
      case interpolatedLiterals if interpolatedLiterals.size == literals.size =>
        val languages = for {
          interpolated <- interpolatedLiterals
          reference <- interpolated.reference

          langId = mapping.get(reference.getText)
          if StringUtils.isNotBlank(langId)
        } yield langId

        languages.toSet.toList match {
          case langId :: Nil =>
            val language = Language.findLanguageByID(langId)
            if (language != null) {
              inject(host, interpolatedLiterals, language)
            }
          case _ => // only inject if all interpolations in string concatenation have same language ids
        }

        true
      case _ => false
    }

  @Measure
  private def injectUsingComment(host: PsiElement, literals: Seq[StringLiteral])
                                (implicit support: ScalaLanguageInjectionSupport,
                                 registrar: MultiHostRegistrar): Boolean = {
    val injection = support.findCommentInjection(host, null)
    if (injection == null) return false
    val langId: String = injection.getInjectedLanguageId
    if (StringUtils.isBlank(langId)) return false
    val language = Language.findLanguageByID(langId)
    if (language == null) return false

    inject(host, literals, language)

    true
  }

  private def inject(host: PsiElement,
                     literals: Seq[StringLiteral],
                     language: Language,
                     prefix: String = "",
                     suffix: String = "")
                    (implicit support: ScalaLanguageInjectionSupport,
                     registrar: MultiHostRegistrar): Unit = {
    registrar.startInjecting(language)

    literals.zipWithIndex.foreach { case (literal, literalIdx) =>
      val litPrefix = if (literalIdx == 0) prefix else ""
      val litSuffix = if (literalIdx == literals.size - 1) suffix else ""

      if (literal.isMultiLineString) {
        val rangesCollected = extractMultiLineStringRanges(literal)

        for ((lineRange, lineIdx) <- rangesCollected.zipWithIndex) {
          val isFirstLine = lineIdx == 0
          val isLastLine = lineIdx == rangesCollected.length - 1

          val prefixActual = if (isFirstLine) litPrefix else ""
          val suffixActual = if (isLastLine) litSuffix else ""

          // capture new line symbol
          val rangeActual = if (isLastLine) lineRange else lineRange.grown(1)
          registrar.addPlace(prefixActual, suffixActual, literal, rangeActual)
        }
      } else {
        registrar.addPlace(litPrefix, litSuffix, literal, getRangeInElement(literal))
      }
    }

    registrar.doneInjecting()
    InjectorUtils.registerSupport(support, true, host, language)
  }

  private def injectUsingAnnotation(host: PsiElement, literals: Seq[StringLiteral])
                                   (implicit support: ScalaLanguageInjectionSupport,
                                    registrar: MultiHostRegistrar): Boolean =
    host match {
      case expression: ScExpression if isDfaEnabled =>
        val annotationName = myInjectionConfiguration.getAdvancedConfiguration.getLanguageAnnotationClass
        injectUsingAnnotation(expression, literals, annotationName)
      case _                                        => false
    }

  @Measure
  private def injectUsingAnnotation(host: ScExpression, literals: Seq[StringLiteral],
                                    annotationQualifiedName: String)
                                   (implicit support: ScalaLanguageInjectionSupport,
                                    registrar: MultiHostRegistrar): Boolean = {
    val maybeAnnotationOwner = host match {
      case literal: ScLiteral =>
        if (literal.isString) annotationOwnerForStringLiteral(literal)
        else None
      case _ =>
        annotationOwnerFor(host) //.orElse(implicitAnnotationOwnerFor(host)) // TODO implicit conversion checking (SCL-2599), disabled (performance reasons)
    }

    val maybePair = for {
      annotationOwner <- maybeAnnotationOwner
      annotation <- annotationOwner.getAnnotations.find(_.getQualifiedName == annotationQualifiedName)
      languageId <- readAttribute(annotation, "value")
      language = InjectedLanguage.findLanguageById(languageId)
      if language != null
    } yield (annotation, language)

    for {
      (annotation, language) <- maybePair
    } inject(
      host,
      literals,
      language,
      readAttribute(annotation, "prefix").mkString,
      readAttribute(annotation, "suffix").mkString
    )

    maybePair.isDefined
  }

  // FIXME: looks like this does not work for now, see SCL-15463
  @Measure
  private def injectUsingIntention(host: PsiElement, literals: Seq[StringLiteral])
                                  (implicit support: ScalaLanguageInjectionSupport,
                                   registrar: MultiHostRegistrar): Boolean = {
    host match {
      case injectionHost: PsiLanguageInjectionHost =>
        val registry = TemporaryPlacesRegistry.getInstance(host.getProject)
        registry.getLanguageFor(injectionHost, injectionHost.getContainingFile) match {
          case lang: InjectedLanguage =>
            performSimpleInjection(literals, lang, new BaseInjection(support.getId), injectionHost, registrar, support)
            true
          case _ => false
        }
      case _ => false
    }
  }
}

object ScalaLanguageInjector {
  
  private val ElementsToInjectIn = ju.Arrays.asList(
    classOf[ScInterpolatedStringLiteral],
    classOf[ScStringLiteral],
    classOf[ScInfixExpr]
  )

  private type StringLiteral = ScLiteral with PsiLanguageInjectionHost
  private type AnnotationOwner = PsiAnnotationOwner with PsiElement
  private type MaybeAnnotationOwner = Option[AnnotationOwner]

  private[this] object CachedAnnotationOwner {

    private[this] val OwnerKey = Key.create[(MaybeAnnotationOwner, Long)]("scala.annotation.owner")

    def apply(literal: ScLiteral, modCount: Long): Option[MaybeAnnotationOwner] = literal.getCopyableUserData(OwnerKey) match {
      case null => None
      case (result, cachedModCount) if (modCount == cachedModCount || isEdt) && result.forall(_.isValid) => Some(result)
      case _ => None
    }

    def update(literal: ScLiteral, maybeOwner: (MaybeAnnotationOwner, Long)): Unit =
      literal.putCopyableUserData(OwnerKey, maybeOwner)
  }

  @inline
  private def isEdt: Boolean = ApplicationManager.getApplication.isDispatchThread

  @Measure
  private def injectUsingPatterns(host: PsiElement, literals: Seq[StringLiteral],
                                  injections: ju.List[BaseInjection])
                                 (implicit support: ScalaLanguageInjectionSupport,
                                  registrar: MultiHostRegistrar): Boolean = {
    /** acceptsPsiElement under the hood does many resolving
     *
     * @see [[org.jetbrains.plugins.scala.patterns.ScalaElementPattern]]
     *      [[org.intellij.plugins.intelliLang.inject.config.BaseInjection#acceptsPsiElement(com.intellij.psi.PsiElement)]]
     *      [[./scalaInjections.xml]] */
    if (shouldAvoidResolve) return false

    val injectionOpt = injections.iterator.asScala.find(_.acceptsPsiElement(host))
    injectionOpt match {
      case Some(injection) =>
        val langId = injection.getInjectedLanguageId
        val language = InjectedLanguage.findLanguageById(langId)
        if (language != null) {
          val injectedLanguage = InjectedLanguage.create(langId, injection.getPrefix, injection.getSuffix, false)
          performSimpleInjection(literals, injectedLanguage, injection, host, registrar, support)
        }
        true
      case _ =>
        false
    }
  }

  private def annotationOwnerForStringLiteral(stringLiteral: ScLiteral): MaybeAnnotationOwner = {
    val modCount: Long = BlockModificationTracker(stringLiteral).getModificationCount
    CachedAnnotationOwner(stringLiteral, modCount) match {
      case Some(result) => result
      case _ =>
        val result = annotationOwnerFor(stringLiteral)
        CachedAnnotationOwner(stringLiteral) = (result, modCount)
        result
    }
  }

  @tailrec
  private def annotationOwnerFor(expression: ScExpression): MaybeAnnotationOwner = expression.getParent match {
    case pattern: ScPatternDefinition                   => Some(pattern)
    case variable: ScVariableDefinition                 => Some(variable)
    case param: ScParameter                             => Some(param)
    case _: ScArgumentExprList                          => parameterOf(expression)
    case tuple: ScTuple if tuple.isCall                 => parameterOf(expression)
    case ScAssignment(leftExpression, _)                => assignmentTarget(leftExpression)
    case parExpr: ScParenthesisedExpr                   => annotationOwnerFor(parExpr)
    case infix: ScInfixExpr                             =>
      if (expression == infix.getFirstChild)
        if (isSafeCall(infix))
          annotationOwnerFor(infix)
        else
          None
      else
        parameterOf(expression)
    case safeCall: ScExpression if isSafeCall(safeCall) => annotationOwnerFor(safeCall)
    case _                                              => None
  }

  private def extractMultiLineStringRanges(literal: ScLiteral): collection.Seq[TextRange] = {
    val range = getRangeInElement(literal)
    val rangeStartOffset = range.getStartOffset

    val rangesCollected = mutable.ListBuffer[TextRange]()
    val extractedText = range.substring(literal.getText)
    val marginChar = MultilineStringUtil.getMarginChar(literal)

    var count = 0
    val lines = extractedText.linesIterator

    for (line <- lines) {
      val lineLength = line.length
      val wsPrefixLength = line.segmentLength(_.isWhitespace)

      val lineHasMargin = wsPrefixLength < line.length && line.charAt(wsPrefixLength) == marginChar

      val shift = if (lineHasMargin) wsPrefixLength + 1 else 0
      val start = rangeStartOffset + count + shift
      rangesCollected += TextRange.from(start, lineLength - shift)

      count += lineLength + 1
    }

    if (extractedText.endsWith('\n')) {
      // last empty line is not treat as a line by WrappedString,
      // but we need to add an empty range in order to be able to edit this line in `Edit code fragment` panel
      val end = count + 1 + rangeStartOffset
      rangesCollected += TextRange.create(end, end)
    }
    if (rangesCollected.isEmpty) {
      rangesCollected += TextRange.create(rangeStartOffset, rangeStartOffset)
    }

    rangesCollected
  }

  private def performSimpleInjection(literals: Seq[StringLiteral], injectedLanguage: InjectedLanguage,
                                     injection: BaseInjection, host: PsiElement, registrar: MultiHostRegistrar,
                                     support: LanguageInjectionSupport): Unit = {
    val trinities = for {
      literal <- literals
      range <- literal match {
        case multiLineString: ScLiteral if multiLineString.isMultiLineString =>
          extractMultiLineStringRanges(multiLineString)
        case _ =>
          injection.getInjectedArea(literal).asScala
      }
    } yield Trinity.create(
      literal: PsiLanguageInjectionHost,
      injectedLanguage,
      range
    )

    val language = injectedLanguage.getLanguage
    InjectorUtils.registerInjection(language, trinities.asJava, host.getContainingFile, registrar)
    InjectorUtils.registerSupport(support, true, host, language)
  }

  private def getRangeInElement(element: ScLiteral): TextRange =
    ElementManipulators.getNotNullManipulator(element).getRangeInElement(element)

  private def isSafeCall(testExpr: ScExpression): Boolean = {
    val name = testExpr match {
      case MethodInvocation(ElementText(text), _) => text
      case ref: ScReferenceExpression => ref.refName
      case _ => null
    }

    name match {
      case "stripMargin" | "+" => true
      case _ => false
    }
  }

  private[this] def parameterOf(argument: ScExpression): MaybeAnnotationOwner = {
    if (shouldAvoidResolve) return None

    def getParameter(methodInv: MethodInvocation, index: Int): Option[PsiElement with PsiAnnotationOwner] = {
      if (index == -1) None else {
        val refOpt = methodInv.getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression]
        refOpt.flatMap { ref =>
          ref.resolve().toOption match {
            case Some(f: ScFunction) =>
              val parameters = f.parameters
              parameters.lift(index)
            case Some(m: PsiMethod)  =>
              val parameters = m.parameters
              parameters.lift(index).safeMap(_.getModifierList)
            case _ => None
          }
        }
      }
    }

    argument.getParent match {
      case args: ScArgumentExprList =>
        args.getParent match {
          case call: ScMethodCall => getParameter(call, args.exprs.indexOf(argument))
          case _ => None
        }
      case tuple: ScTuple if tuple.isCall =>
        getParameter(tuple.getContext.asInstanceOf[ScInfixExpr], tuple.exprs.indexOf(argument))
      case infix: ScInfixExpr => getParameter(infix, 0)
      case _ => None
    }
  }

  //avoid reference resolving on EDT thread
  private def shouldAvoidResolve: Boolean =
    isEdt && !ApplicationManager.getApplication.isUnitTestMode

  // map(x) = y check
  private[this] def assignmentTarget(leftExpression: ScExpression) = leftExpression match {
    case _: ScMethodCall => None
    case ScReference(target) =>
      val context = target match {
        case p: ScReferencePattern => p.getParent.getParent
        case field: PsiField => field.getModifierList
        case _ => target
      }

      context match {
        case owner: AnnotationOwner => Some(owner)
        case _ => None
      }
    case _ => None
  }

}
