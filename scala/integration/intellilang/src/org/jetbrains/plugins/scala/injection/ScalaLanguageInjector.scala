package org.jetbrains.plugins.scala
package injection

import java.{util => ju}

import com.intellij.lang.Language
import com.intellij.lang.injection.{MultiHostInjector, MultiHostRegistrar}
import com.intellij.openapi.util.{TextRange, Trinity}
import com.intellij.psi._
import org.apache.commons.lang3.StringUtils
import org.intellij.plugins.intelliLang.Configuration
import org.intellij.plugins.intelliLang.inject._
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
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

import scala.collection.{JavaConverters, immutable, mutable}

/**
  * @author Pavel Fatin
  * @author Dmitry Naydanov
  */
final class ScalaLanguageInjector(myInjectionConfiguration: Configuration) extends MultiHostInjector {

  override def elementsToInjectIn: ju.List[Class[_ <: PsiElement]] = ju.Arrays.asList(
    classOf[ScLiteral],
    classOf[ScInfixExpr]
  )

  override def getLanguagesToInject(registrar: MultiHostRegistrar,
                                    host: PsiElement): Unit =
    LanguageInjectionSupport.EP_NAME.findExtension(classOf[ScalaLanguageInjectionSupport]) match {
      case null =>
      case support =>
        literalsOf(host) match {
          case Seq() =>
          case literals =>
            implicit val s: ScalaLanguageInjectionSupport = support
            implicit val r: MultiHostRegistrar = registrar

            if (injectUsingIntention(host, literals))
              return
            if (injectUsingComment(host, literals))
              return
            if (injectInInterpolation(host, literals))
              return

            if (ScalaProjectSettings.getInstance(host.getProject).isDisableLangInjection)
              return

            if (injectUsingAnnotation(host, literals))
              return
            if (injectUsingPatterns(host, literals))
              return

          // final expression uses return for easy debugging
        }
    }

  /**
    * @return 1) `host` itself - if host is string literal that is not inside string concatenation <br>
    *         2) string concatenation operands if `host` is a top level concatenation expression <br>
    *         3) empty collection otherwise
    */
  private def literalsOf(host: PsiElement): Seq[ScLiteral] = host.getParent match {
    case ScInfixExpr(_, ElementText("+"), _) =>
      // if string literal is inside concatenations skip it
      // we would like to process top-level expressions only
      Seq.empty
    case _ =>
      val expressions = host.depthFirst {
        case expression: ScExpression => !expression.getParent.isInstanceOf[ScInterpolatedStringLiteral]
        case _ => true
      }.toList.filter {
        _.isInstanceOf[ScExpression]
      }.map {
        _.asInstanceOf[ScExpression]
      }

      val suitable = expressions.forall {
        case l: ScLiteral if l.isString => true
        case _: ScInterpolatedPrefixReference |
             _: ScInfixExpr => true
        case r: ScReferenceExpression if r.getText == "+" => true
        case expression => expression.getParent.isInstanceOf[ScInterpolatedStringLiteral]
      }

      if (suitable)
        expressions.filter {
          case literal: ScLiteral => literal.isString
          case _ => false
        }.map {
          _.asInstanceOf[ScLiteral]
        }
      else
        Seq.empty
  }

  private def injectInInterpolation(host: PsiElement, literals: Seq[ScLiteral])
                                   (implicit support: ScalaLanguageInjectionSupport,
                                    registrar: MultiHostRegistrar): Boolean = {
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
      interpolatedLiterals.flatMap(extractLanguageId).toSet.toList match {
        case langId :: Nil =>
          Language.findLanguageByID(langId) match {
            case null =>
            case language => inject(host, interpolatedLiterals, language)
          }
        case _ => // only inject if all interpolations in string concatenation have same language ids
      }
      true
    } else {
      false
    }
  }

  private def injectUsingComment(host: PsiElement, literals: Seq[ScLiteral])
                                (implicit support: ScalaLanguageInjectionSupport,
                                 registrar: MultiHostRegistrar): Boolean =
    support.findCommentInjection(host, null) match {
      case null => false
      case injection =>
        Language.findLanguageByID(injection.getInjectedLanguageId) match {
          case null => false
          case language =>
            inject(host, literals, language)
            true
        }
    }

  private def inject(host: PsiElement,
                     literals: Seq[ScLiteral],
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

  private def injectUsingAnnotation(host: PsiElement, literals: Seq[ScLiteral])
                                   (implicit support: ScalaLanguageInjectionSupport,
                                    registrar: MultiHostRegistrar): Boolean = {
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

    val maybePair = for {
      annotation <- annotationOpt
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


  private def injectUsingPatterns(host: PsiElement, literals: Seq[ScLiteral])
                                 (implicit support: ScalaLanguageInjectionSupport,
                                  registrar: MultiHostRegistrar): Boolean = {
    val injectionsList = myInjectionConfiguration.getInjections(support.getId)
    val injections = injectionsList.iterator()

    var done = false
    while (!done && injections.hasNext) {
      val injection: BaseInjection = injections.next()

      if (injection.acceptsPsiElement(host)) {
        val languageId = injection.getInjectedLanguageId
        InjectedLanguage.findLanguageById(languageId) match {
          case null =>
          case _ =>
            performSimpleInjection(
              host,
              literals,
              InjectedLanguage.create(languageId, injection.getPrefix, injection.getSuffix, false),
              injection
            )
        }

        done = true
      }
    }

    done
  }

  // FIXME: looks like this does not work for now, see SCL-15463
  private def injectUsingIntention(host: PsiElement, literals: Seq[ScLiteral])
                                  (implicit support: ScalaLanguageInjectionSupport,
                                   registrar: MultiHostRegistrar): Boolean = host match {
    case injectionHost: PsiLanguageInjectionHost =>
      TemporaryPlacesRegistry.getInstance(host.getProject)
        .getLanguageFor(injectionHost, injectionHost.getContainingFile) match {
        case null => false
        case language =>
          performSimpleInjection(injectionHost, literals, language, new BaseInjection(support.getId))
          true
        case _ => false
      }
    case _ => false
  }

  @annotation.tailrec
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

  private def extractMultiLineStringRanges(literal: ScLiteral): Seq[TextRange] = {
    val range = getRangeInElement(literal)
    val rangeStartOffset = range.getStartOffset

    val rangesCollected = mutable.MutableList[TextRange]()
    val extractedText = range.substring(literal.getText)
    val marginChar = MultilineStringUtil.getMarginChar(literal)

    var count = 0
    val lines = new immutable.WrappedString(extractedText).lines

    for (line <- lines) {
      val lineLength = line.length
      val wsPrefixLength = line.prefixLength(_.isWhitespace)

      val lineHasMargin = wsPrefixLength < line.length && line.charAt(wsPrefixLength) == marginChar

      val start = if (lineHasMargin) wsPrefixLength + 1 + count else count
      val end = count + lineLength
      rangesCollected += new TextRange(start + rangeStartOffset, end + rangeStartOffset)

      count += lineLength + 1
    }
    if (extractedText.last == '\n') {
      // last empty line is not treat as a line by WrappedString,
      // but we need to add an empty range in order to be able to edit this line in `Edit code fragment` panel
      val end = count + 1 + rangeStartOffset
      rangesCollected += new TextRange(end, end)
    }
    if (rangesCollected.isEmpty) {
      rangesCollected += new TextRange(rangeStartOffset, rangeStartOffset)
    }

    rangesCollected
  }

  private def performSimpleInjection(host: PsiElement, literals: Seq[ScLiteral],
                                     injectedLanguage: InjectedLanguage,
                                     injection: BaseInjection)
                                    (implicit support: ScalaLanguageInjectionSupport,
                                     registrar: MultiHostRegistrar): Unit = {
    import JavaConverters._
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
}
