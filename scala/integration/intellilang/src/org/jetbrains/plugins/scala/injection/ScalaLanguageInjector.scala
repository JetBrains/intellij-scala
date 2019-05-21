package org.jetbrains.plugins.scala
package injection

import java.util

import com.intellij.lang.Language
import com.intellij.lang.injection.{MultiHostInjector, MultiHostRegistrar}
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.{TextRange, Trinity}
import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import org.apache.commons.lang3.StringUtils
import org.intellij.plugins.intelliLang.Configuration
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.intellij.plugins.intelliLang.inject.{InjectedLanguage, InjectorUtils, LanguageInjectionSupport, TemporaryPlacesRegistry}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.injection.ScalaLanguageInjector._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.readAttribute
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPrefixReference
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.immutable.WrappedString
import scala.collection.mutable

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov
 */

class ScalaLanguageInjector(myInjectionConfiguration: Configuration) extends MultiHostInjector {
  override def elementsToInjectIn: util.List[Class[_ <: PsiElement]] = ContainerUtil.list(
    classOf[ScLiteral],
    classOf[ScInfixExpr]
  )

  override def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement): Unit = {
    if (support == null)
      return

    val literals: Seq[ScLiteral] = literalsOf(host)
    if (literals.isEmpty)
      return

    if (injectUsingIntention(registrar, host, literals))
      return
    if (injectUsingComment(registrar, host, literals))
      return
    if (injectInInterpolation(registrar, host, literals))
      return

    if (ScalaProjectSettings.getInstance(host.getProject).isDisableLangInjection)
      return

    if (injectUsingAnnotation(registrar, host, literals))
      return
    if (injectUsingPatterns(registrar, host, literals))
      return

    // final expression uses return for easy debugging
  }

  /**
   * @return 1) `host` itself - if host is string literal that is not inside string concatenation <br>
   *         2) string concatenation operands if `host` is a top level concatenation expression <br>
   *         3) empty collection otherwise
   */
  private def literalsOf(host: PsiElement): Seq[ScLiteral] = {
    host.getParent match {
      case infix: ScInfixExpr if "+" == infix.getInvokedExpr.getText =>
        // if string literal is inside concatenations skip it
        // we would like to process top-level expressions only
        return Seq.empty
      case _ =>
    }

    val expressions = host.depthFirst {
      case (_: ScExpression) && Parent(_: ScInterpolatedStringLiteral) => false
      case _ => true
    }.filter(_.isInstanceOf[ScExpression]).toList

    val suitable = expressions.forall({
      case l: ScLiteral if l.isString => true
      case _: ScInterpolatedPrefixReference => true
      case r: ScReferenceExpression if r.getText == "+" => true
      case _: ScInfixExpr => true
      case (_: ScExpression) && Parent(_: ScInterpolatedStringLiteral) => true
      case _ => false
    })

    if (suitable) {
      expressions.collect { case x: ScLiteral if x.isString => x }
    } else {
      Seq.empty
    }
  }

  private def injectInInterpolation(registrar: MultiHostRegistrar,
                                    host: PsiElement,
                                    literals: Seq[ScLiteral]): Boolean = {
    val languageByPrefix = ScalaProjectSettings.getInstance(host.getProject).getIntInjectionMapping

    @inline
    def extractLanguageId(interpolated: ScInterpolatedStringLiteral): Option[String] =
      for {
        ref <- interpolated.reference
        langId <- Option(languageByPrefix.get(ref.getText)) if StringUtils.isNotBlank(langId)
      } yield langId

    val interpolatedLiterals: Seq[ScInterpolatedStringLiteral] =
      literals.filterBy[ScInterpolatedStringLiteral]

    if (interpolatedLiterals.size == literals.size) {
      val languages: Seq[String] = interpolatedLiterals.flatMap(extractLanguageId)
      languages.toSet.toList match {
        case langId :: Nil =>
          val language = Language.findLanguageByID(langId)
          if (language != null) {
            inject(registrar, host, interpolatedLiterals, language)
          }
        case _ => // only inject if all interpolations in string concatenation have same language ids
      }
      true
    } else {
      false
    }
  }

  private def injectUsingComment(registrar: MultiHostRegistrar,
                                 host: PsiElement,
                                 literals: Seq[ScLiteral]): Boolean = {
    val injection = support.findCommentInjection(host, null)
    if (injection == null) return false
    val langId: String = injection.getInjectedLanguageId
    if (langId == null) return false
    val language = Language.findLanguageByID(langId)
    if (language == null) return false

    inject(registrar, host, literals, language)

    true
  }

  private def inject(registrar: MultiHostRegistrar,
                     host: PsiElement,
                     literals: Seq[ScLiteral],
                     language: Language,
                     prefix: String = "",
                     suffix: String = ""): Unit = {
    registrar.startInjecting(language)

    literals.zipWithIndex.foreach { case (literal, literalIdx) =>
      val litPrefix = if (literalIdx == 0) prefix else ""
      val litSuffix = if (literalIdx == literals.size - 1) suffix else ""

      if (literal.isMultiLineString) {
        val rangesCollected = extractMultiLineStringRanges(literal)

        for ((lineRange, lineIdx) <- rangesCollected.zipWithIndex) {
          val isLastLine = lineIdx == rangesCollected.length - 1

          val prefixActual = if (lineIdx == 0) litPrefix else ""
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


  private def injectUsingAnnotation(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    Configuration.getInstance.getAdvancedConfiguration.getDfaOption match {
      case Configuration.DfaOption.OFF => return false
      case _ =>
    }

    val expression = host match {
      case e: ScExpression => e
      case _ => return false
    }

    // TODO implicit conversion checking (SCL-2599), disabled (performance reasons)
    val annotationOwner = expression match {
      case lit: ScLiteral => lit.getAnnotationOwner(annotationOwnerFor(_))
      case _ => annotationOwnerFor(expression) //.orElse(implicitAnnotationOwnerFor(literal))
    }
    val annotationOpt = annotationOwner.flatMap {
      _.getAnnotations.find {
        _.getQualifiedName == myInjectionConfiguration.getAdvancedConfiguration.getLanguageAnnotationClass
      }
    }

    ((for {
      annotation <- annotationOpt
      languageId <- readAttribute(annotation, "value")
      language <- InjectedLanguage.findLanguageById(languageId).toOption
    } yield {
      val annotationPrefix = readAttribute(annotation, "prefix").mkString
      val annotationSuffix = readAttribute(annotation, "suffix").mkString
      inject(registrar, host, literals, language, annotationPrefix, annotationSuffix)
    }): @inline) match {
      case Some(_) => true
      case None => false
    }
  }


  private def injectUsingPatterns(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    val injectionsList = myInjectionConfiguration.getInjections(support.getId)
    val injections = injectionsList.iterator()

    var done = false
    while (!done && injections.hasNext) {
      val injection: BaseInjection = injections.next()

      if (injection.acceptsPsiElement(host)) {
        val language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId)
        if (language != null) {
          val injectedLanguage = InjectedLanguage.create(injection.getInjectedLanguageId, injection.getPrefix, injection.getSuffix, false)
          performSimpleInjection(literals, injectedLanguage, injection, host, registrar, support)
        }
        done = true
      }
    }

    done
  }

  // FIXME: looks like this does not work for now, see SCL-15463
  private def injectUsingIntention(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    val hostActual = host match {
      case e: PsiLanguageInjectionHost => e
      case _ => return false
    }
    val registry = TemporaryPlacesRegistry.getInstance(host.getProject)
    registry.getLanguageFor(hostActual, hostActual.getContainingFile) match {
      case lang: InjectedLanguage =>
        performSimpleInjection(literals, lang, new BaseInjection(support.getId), hostActual, registrar, support)
        true
      case _ =>
        false
    }
  }

  @tailrec
  private def annotationOwnerFor(child: ScExpression): Option[PsiAnnotationOwner with PsiElement] = child.getParent match {
    case pattern: ScPatternDefinition => Some(pattern)
    case variable: ScVariableDefinition => Some(variable)
    case _: ScArgumentExprList => parameterOf(child)
    case assignment: ScAssignment => assignmentTarget(assignment)
    case infix: ScInfixExpr if child == infix.getFirstChild =>
      if (isSafeCall(infix)) annotationOwnerFor(infix) else None
    case _: ScInfixExpr => parameterOf(child)
    case tuple: ScTuple if tuple.isCall => parameterOf(child)
    case param: ScParameter => Some(param)
    case parExpr: ScParenthesisedExpr => annotationOwnerFor(parExpr)
    case safeCall: ScExpression if isSafeCall(safeCall) => annotationOwnerFor(safeCall)
    case _ => None
  }

  private def implicitAnnotationOwnerFor(literal: ScLiteral): Option[PsiAnnotationOwner] = {
    literal.implicitElement().flatMap(_.asOptionOf[ScFunction]).flatMap(_.parameters.headOption)
  }

  private def assignmentTarget(assignment: ScAssignment): Option[PsiAnnotationOwner with PsiElement] = {
    val left = assignment.leftExpression
    // map(x) = y check
    left match {
      case _: ScMethodCall => None
      case ref: ScReference =>
        ref.resolve().toOption
          .map(contextOf)
          .flatMap(_.asOptionOf[PsiAnnotationOwner with PsiElement])
      case _ => None
    }
  }

  private def parameterOf(argument: ScExpression): Option[PsiAnnotationOwner with PsiElement] = {
    def getParameter(methodInv: MethodInvocation, index: Int) = {
      if (index == -1) None
      else methodInv.getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression] flatMap {
        ref =>
          ref.resolve().toOption match {
            case Some(f: ScFunction) =>
              val parameters = f.parameters
              if (parameters.isEmpty) None
              else Some(parameters(index.min(parameters.size - 1)))
            case Some(m: PsiMethod) =>
              val parameters = m.parameters
              if (parameters.isEmpty) None else parameters(index.min(parameters.size - 1)).getModifierList.toOption
            case _ => None
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

  private def contextOf(element: PsiElement): PsiElement = element match {
    case p: ScReferencePattern => p.getParent.getParent
    case field: PsiField => field.getModifierList
    case _ => element
  }
}

object ScalaLanguageInjector {
  private[this] val scalaStringLiteralManipulator = new ScalaInjectedStringLiteralManipulator
  private[this] val safeMethodsNames = List("stripMargin", "+")

  private val support: LanguageInjectionSupport =
    Extensions.getExtensions(LanguageInjectionSupport.EP_NAME).find(_.getId == "scala").orNull

  private type InjectionsList = util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]

  private def extractMultiLineStringRanges(literal: ScLiteral): Seq[TextRange] = {
    val range = getRangeInElement(literal)
    val rangeStartOffset = range.getStartOffset

    val rangesCollected = mutable.MutableList[TextRange]()
    val extractedText = range.substring(literal.getText)
    val marginChar = MultilineStringUtil.getMarginChar(literal)

    var count = 0
    val lines = new WrappedString(extractedText).lines

    for (line <- lines) {
      val lineLength = line.length
      val wsPrefixLength = line.prefixLength(_.isWhitespace)

      val lineHasMargin = wsPrefixLength < line.length && line.charAt(wsPrefixLength) == marginChar

      val shift = if (lineHasMargin) wsPrefixLength + 1 else 0
      val start = rangeStartOffset + count + shift
      rangesCollected += TextRange.from(start, lineLength - shift)

      count += lineLength + 1
    }

    if (extractedText.last == '\n') {
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

  private def handleInjectionImpl(literal: ScLiteral, language: InjectedLanguage,
                                  currentInjection: BaseInjection, list: InjectionsList): Unit = {
    val ranges: Seq[TextRange] = literal match {
      case multiLineString: ScLiteral if multiLineString.isMultiLineString =>
        extractMultiLineStringRanges(multiLineString)
      case scLiteral =>
        currentInjection.getInjectedArea(scLiteral).asScala
    }
    ranges.foreach { range =>
      list.add(Trinity.create(literal, language, range))
    }
  }

  private def performSimpleInjection(literals: scala.Seq[ScLiteral], injectedLanguage: InjectedLanguage,
                                     injection: BaseInjection, host: PsiElement, registrar: MultiHostRegistrar,
                                     support: LanguageInjectionSupport): Unit = {
    val list: InjectionsList = new util.ArrayList

    literals.foreach(handleInjectionImpl(_, injectedLanguage, injection, list))

    val language = injectedLanguage.getLanguage
    InjectorUtils.registerInjection(language, list, host.getContainingFile, registrar)
    InjectorUtils.registerSupport(support, true, host, language)
  }

  private def getRangeInElement(element: ScLiteral): TextRange =
    scalaStringLiteralManipulator.getRangeInElement(element)

  private def isSafeCall(testExpr: ScExpression): Boolean = testExpr match {
    case methodInv: MethodInvocation => safeMethodsNames.contains(methodInv.getEffectiveInvokedExpr.getText)
    case ref: ScReferenceExpression => safeMethodsNames.contains(ref.refName)
    case _ => false
  }
}
