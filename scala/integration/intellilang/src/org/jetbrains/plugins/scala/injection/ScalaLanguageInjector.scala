package org.jetbrains.plugins.scala
package injection

import java.util

import com.intellij.lang.injection.{MultiHostInjector, MultiHostRegistrar}
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.{TextRange, Trinity}
import com.intellij.psi._
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
  override def elementsToInjectIn: util.List[Class[_ <: PsiElement]] = List[Class[_ <: PsiElement]](classOf[ScLiteral], classOf[ScInfixExpr]).asJava

  override def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement): Unit = {
    val literals = literalsOf(host)
    if (literals.isEmpty) return

    if (injectUsingIntention(registrar, host, literals))
      return
    if (injectInInterpolation(registrar, host, literals))
      return

    if (ScalaProjectSettings.getInstance(host.getProject).isDisableLangInjection)
      return

    if (injectUsingAnnotation(registrar, host, literals))
      return

    injectUsingPatterns(registrar, host, literals)
  }

  private def literalsOf(host: PsiElement): Seq[ScLiteral] = {
    host.getParent match {
      case infix: ScInfixExpr if "+" == infix.getInvokedExpr.getText => return Seq.empty // process top-level expressions only
      case _ =>
    }

    val expressions = host.depthFirst {
      case injectedExpr: ScExpression if injectedExpr.getParent.isInstanceOf[ScInterpolatedStringLiteral] => false
      case _ => true
    }.filter(_.isInstanceOf[ScExpression]).toList

    val suitable = expressions forall {
      case l: ScLiteral if l.isString => true
      case _: ScInterpolatedPrefixReference => true
      case r: ScReferenceExpression if r.getText == "+" => true
      case _: ScInfixExpr => true
      case injectedExpr: ScExpression if injectedExpr.getParent.isInstanceOf[ScInterpolatedStringLiteral] => true
      case _ => false
    }

    if (suitable)
      expressions collect {
        case x: ScLiteral if x.isString => x
      }
    else
      Seq.empty
  }

  private def injectUsingAnnotation(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    Configuration.getInstance.getAdvancedConfiguration.getDfaOption match {
      case Configuration.DfaOption.OFF => return false
      case _ =>
    }

    val expression = host.asInstanceOf[ScExpression]
    // TODO implicit conversion checking (SCL-2599), disabled (performance reasons)
    val annotationOwner = expression match {
      case lit: ScLiteral => lit.getAnnotationOwner(annotationOwnerFor(_))
      case _ => annotationOwnerFor(expression) //.orElse(implicitAnnotationOwnerFor(literal))
    }

    val annotation = annotationOwner flatMap {
      _.getAnnotations find {
        _.getQualifiedName == myInjectionConfiguration.getAdvancedConfiguration.getLanguageAnnotationClass
      }
    }

    val languageId = annotation.flatMap(readAttribute(_, "value"))
    val language = languageId.flatMap(it => InjectedLanguage.findLanguageById(it).toOption)

    language.foreach { it =>
      registrar.startInjecting(it)
      literals.zipWithIndex foreach { p =>
        val (literal, i) = p
        val prefix = if (i == 0) annotation.flatMap(readAttribute(_, "prefix")).mkString else ""
        val suffix = if (i == literals.size - 1) annotation.flatMap(readAttribute(_, "suffix")).mkString else ""

        if (!literal.isMultiLineString) {
          registrar.addPlace(prefix, suffix, literal, getRangeInElement(literal))
        } else {
          val rangesCollected = extractMultiLineStringRanges(literal)

          for ((lineRange, index) <- rangesCollected.zipWithIndex) {
            val prefixActual = if (index == 0) prefix else " "
            val suffixActual = if (index == rangesCollected.length - 1) suffix else " "
            registrar.addPlace(prefixActual, suffixActual, literal, lineRange)
          }
        }
      }
      registrar.doneInjecting()
    }

    language.isDefined
  }

  private def injectUsingPatterns(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    withInjectionSupport { support =>
      val injections = myInjectionConfiguration.getInjections(support.getId).iterator()

      var done = false

      while (!done && injections.hasNext) {
        val injection = injections.next()

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
    } getOrElse false
  }

  private def injectUsingIntention(registrar: MultiHostRegistrar, element: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    val registry = TemporaryPlacesRegistry.getInstance(element.getProject)

    element match {
      case host: PsiLanguageInjectionHost =>
        (for {
          injectedLanguage <- Option(registry.getLanguageFor(host, element.getContainingFile))
          support <- injectionSupport
        } yield {
          performSimpleInjection(literals, injectedLanguage, new BaseInjection(support.getId), host, registrar, support)
          true
        }).getOrElse(false)
      case _ => false
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

  private def injectInInterpolation(registrar: MultiHostRegistrar, hostElement: PsiElement,
                                    literals: scala.Seq[ScLiteral]) = {
    hostElement match {
      case host: PsiLanguageInjectionHost =>
        withInjectionSupport { support =>
          val mapping = ScalaProjectSettings.getInstance(host.getProject).getIntInjectionMapping
          val allInjections =
            new mutable.HashMap[InjectedLanguage, util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]]()

          literals filter {
            case interpolated: ScInterpolatedStringLiteral
              if interpolated.reference.exists(r => mapping.containsKey(r.getText)) => true
            case _ => false
          } foreach {
            case literal: ScInterpolatedStringLiteral =>
              val languageId = mapping.get(literal.reference.get.getText)
              val injectedLanguage = InjectedLanguage.create(languageId)
              val list = new util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]

              if (injectedLanguage != null) {
                handleInjectionImpl(literal, injectedLanguage, new BaseInjection(support.getId), list)
                allInjections(injectedLanguage) = list
              }
          }

          for ((injectedLang, list) <- allInjections) {
            val lang = injectedLang.getLanguage
            InjectorUtils.registerInjection(lang, list, host.getContainingFile, registrar)
            InjectorUtils.registerSupport(support, true, registrar)
          }

          allInjections.nonEmpty
        } getOrElse false
      case _ => false //something is wrong
    }
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

  private def extractMultiLineStringRanges(literal: ScLiteral): Seq[TextRange] = {
    val range = getRangeInElement(literal)

    val rangesCollected = mutable.MutableList[TextRange]()
    val extractedText = range.substring(literal.getText)
    val margin = String.valueOf(MultilineStringUtil.getMarginChar(literal))

    var count = 0
    val lines = new WrappedString(extractedText).lines

    for (partOfMlLine <- lines) {
      val lineLength = partOfMlLine.length
      val wsPrefixLength = partOfMlLine.prefixLength(_.isWhitespace)

      if (wsPrefixLength != lineLength) {
        val start = if (partOfMlLine.trim.startsWith(margin)) count + 1 + wsPrefixLength else count
        val end = count + lineLength
        rangesCollected += new TextRange(start, end).shiftRight(range.getStartOffset)
      }

      count += lineLength + 1
    }
    if (rangesCollected.isEmpty) {
      rangesCollected += new TextRange(range.getStartOffset, range.getStartOffset)
    }

    rangesCollected
  }

  private def handleInjectionImpl(literal: ScLiteral, language: InjectedLanguage, currentInjection: BaseInjection,
                                  list: util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]): Unit = {
    literal match {
      case multiLineString: ScLiteral if multiLineString.isMultiLineString =>
        extractMultiLineStringRanges(multiLineString) foreach { range =>
          list.add(Trinity.create(multiLineString, language, range))
        }
      case scLiteral => currentInjection.getInjectedArea(scLiteral) forEach { range =>
        list.add(Trinity.create(scLiteral, language, range))
      }
    }
  }

  private def withInjectionSupport[T](action: LanguageInjectionSupport => T): Option[T] =
    injectionSupport.map(action)

  private def injectionSupport: Option[LanguageInjectionSupport] =
    Extensions.getExtensions(LanguageInjectionSupport.EP_NAME).find(_.getId == "scala")

  private def performSimpleInjection(literals: scala.Seq[ScLiteral], injectedLanguage: InjectedLanguage,
                                     injection: BaseInjection, host: PsiElement, registrar: MultiHostRegistrar,
                                     support: LanguageInjectionSupport): Unit = {
    val list = new util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]

    literals.foreach(handleInjectionImpl(_, injectedLanguage, injection, list))

    InjectorUtils.registerInjection(injectedLanguage.getLanguage, list, host.getContainingFile, registrar)
    InjectorUtils.registerSupport(support, true, registrar)
  }

  private def getRangeInElement(element: ScLiteral): TextRange =
    scalaStringLiteralManipulator.getRangeInElement(element)

  private def isSafeCall(testExpr: ScExpression): Boolean = testExpr match {
    case methodInv: MethodInvocation => safeMethodsNames.contains(methodInv.getEffectiveInvokedExpr.getText)
    case ref: ScReferenceExpression => safeMethodsNames.contains(ref.refName)
    case _ => false
  }
}
