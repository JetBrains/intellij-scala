package org.jetbrains.plugins.scala
package injection

import com.intellij.lang.injection.{MultiHostRegistrar, MultiHostInjector}
import collection.JavaConversions._
import org.intellij.plugins.intelliLang.Configuration
import com.intellij.openapi.util.{Trinity, TextRange}
import org.intellij.plugins.intelliLang.inject.{TemporaryPlacesRegistry, InjectedLanguage, LanguageInjectionSupport, InjectorUtils}
import com.intellij.openapi.extensions.Extensions
import lang.psi.api.expr._
import lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScVariableDefinition}
import com.intellij.psi._
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.base.{ScInterpolatedStringLiteral, ScReferenceElement, ScLiteral}
import lang.psi.ScalaPsiUtil.readAttribute
import org.jetbrains.plugins.scala.extensions._
import settings._
import collection.immutable.WrappedString
import java.util
import collection.mutable

import ScalaLanguageInjector.extractMultiLineStringRanges
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import lang.psi.impl.expr.ScInterpolatedStringPrefixReference
import lang.psi.api.statements.params.ScParameter
import annotation.tailrec
import org.jetbrains.plugins.scala.util.MultilineStringUtil

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov        
 */

class ScalaLanguageInjector(myInjectionConfiguration: Configuration) extends MultiHostInjector {
  override def elementsToInjectIn = List(classOf[ScLiteral], classOf[ScInfixExpr])

  override def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
    val literals = literalsOf(host)
    if (literals.isEmpty) return

    if (injectUsingIntention(registrar, host, literals) || injectInInterpolation(registrar, host, literals)) return 

    if (ScalaProjectSettings.getInstance(host.getProject).isDisableLangInjection) return
    
    injectUsingAnnotation(registrar, host, literals) || injectUsingPatterns(registrar, host, literals) 
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
      case _: ScInterpolatedStringPrefixReference => true
      case r: ScReferenceExpression if r.getText == "+" => true
      case _: ScInfixExpr => true
      case injectedExpr: ScExpression if injectedExpr.getParent.isInstanceOf[ScInterpolatedStringLiteral] => true
      case _ => false
    }

    if(suitable)
      expressions collect {
        case x: ScLiteral if x.isString => x
      }
    else
      Seq.empty
  }

  def injectUsingAnnotation(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    Configuration.getInstance.getAdvancedConfiguration.getDfaOption match {
      case Configuration.DfaOption.OFF => return false
      case _ =>
    }
    
    val expression = host.asInstanceOf[ScExpression]
    // TODO implicit conversion checking (SCL-2599), disabled (performance reasons)
    val annotationOwner = expression match {
      case lit: ScLiteral => lit getAnnotationOwner (annotationOwnerFor(_))
      case _ => annotationOwnerFor(expression) //.orElse(implicitAnnotationOwnerFor(literal)) 
    } 

    val annotation = annotationOwner flatMap {
      _.getAnnotations find { 
        _.getQualifiedName == myInjectionConfiguration.getAdvancedConfiguration.getLanguageAnnotationClass
      }
    }

    val languageId = annotation flatMap (readAttribute(_, "value"))
    val language = languageId flatMap (it => InjectedLanguage.findLanguageById(it).toOption)

    language foreach { it =>
      registrar startInjecting it
      literals.zipWithIndex foreach { p =>
        val (literal, i) = p
        val prefix = if(i == 0) annotation.flatMap(readAttribute(_, "prefix")).mkString else ""
        val suffix = if(i == literals.size - 1) annotation.flatMap(readAttribute(_, "suffix")).mkString else ""

        if (!literal.isMultiLineString) {
          registrar.addPlace(prefix, suffix, literal, ScalaLanguageInjector getRangeInElement literal)
        } else {
          val rangesCollected = extractMultiLineStringRanges(literal)
          
          for ((lineRange, index) <- rangesCollected.zipWithIndex) {
            registrar.addPlace(if (index == 0) prefix else " ", if (index == rangesCollected.length - 1) suffix else " ", literal, lineRange)
          }
        }
      }
      registrar.doneInjecting()
    }

    language.isDefined
  }

  def injectUsingPatterns(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]) = {
    ScalaLanguageInjector withInjectionSupport { support =>
      val injections = myInjectionConfiguration.getInjections(support.getId).toIterator

      var done = false

      while(!done && injections.hasNext) {
        val injection = injections.next()

        if(injection acceptsPsiElement host) {
          val language = InjectedLanguage findLanguageById injection.getInjectedLanguageId
          if (language != null) {
            val injectedLanguage = InjectedLanguage.create(injection.getInjectedLanguageId, injection.getPrefix, injection.getSuffix, false)
            ScalaLanguageInjector.performSimpleInjection(literals, injectedLanguage, injection, host, registrar, support)
          }
          done = true
        }
      }

      done
    } getOrElse false
  }
  
  def injectUsingIntention(registrar: MultiHostRegistrar, element: PsiElement, literals: scala.Seq[ScLiteral]) = {
    val registry = TemporaryPlacesRegistry getInstance element.getProject

    element match {
      case host: PsiLanguageInjectionHost => Option(registry getLanguageFor (host, element.getContainingFile)) flatMap { injectedLanguage =>
        ScalaLanguageInjector withInjectionSupport { support =>
          ScalaLanguageInjector performSimpleInjection (literals, injectedLanguage, new BaseInjection(support.getId), 
            host, registrar, support)
          true
        } 
      } getOrElse false
      case _ => false 
    }
  }

  
  @tailrec
  final def annotationOwnerFor(child: ScExpression): Option[PsiAnnotationOwner with PsiElement] = child.getParent match {
    case pattern: ScPatternDefinition => Some(pattern)
    case variable: ScVariableDefinition => Some(variable)
    case _: ScArgumentExprList => parameterOf(child)
    case assignment: ScAssignStmt => assignmentTarget(assignment)
    case infix: ScInfixExpr if child == infix.getFirstChild =>
      if (ScalaLanguageInjector isSafeCall infix) annotationOwnerFor(infix) else None
    case infix: ScInfixExpr => parameterOf(child)
    case tuple: ScTuple if tuple.isCall => parameterOf(child)
    case param: ScParameter => Some(param)
    case parExpr: ScParenthesisedExpr => annotationOwnerFor(parExpr)
    case safeCall: ScExpression if ScalaLanguageInjector isSafeCall safeCall => annotationOwnerFor(safeCall)
    case _ => None
  }

  def implicitAnnotationOwnerFor(literal: ScLiteral): Option[PsiAnnotationOwner] = {
    literal.getImplicitConversions()._2.flatMap(_.asOptionOf[ScFunction]).flatMap(_.parameters.headOption)
  }
  
  private def injectInInterpolation(registrar: MultiHostRegistrar, hostElement: PsiElement, 
                                    literals: scala.Seq[ScLiteral]) = {
    hostElement match {
      case host: PsiLanguageInjectionHost =>
        ScalaLanguageInjector withInjectionSupport { support =>
          val mapping = ScalaProjectSettings.getInstance(host.getProject).getIntInjectionMapping
          val allInjections = 
            new util.HashMap[InjectedLanguage, util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]]()
          
          literals filter {
            case interpolated: ScInterpolatedStringLiteral 
              if interpolated.reference exists (mapping containsKey _.getText) => true
            case _ => false
          } foreach {
            case literal: ScInterpolatedStringLiteral => 
              val languageId = mapping get literal.reference.get.getText
              val injectedLanguage = InjectedLanguage create languageId
              val list = new util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]

              if (injectedLanguage != null) {
                ScalaLanguageInjector handleInjectionImpl (literal, injectedLanguage, new BaseInjection(support.getId), list)
                allInjections put (injectedLanguage, list)
              }
          }
          
          val languages = allInjections.keySet().iterator()
          while (languages.hasNext) {
            val lang = languages.next()
            val list = allInjections get lang

            InjectorUtils.registerInjection(lang.getLanguage, list, host.getContainingFile, registrar)
            InjectorUtils.registerSupport(support, true, registrar)
          }
          
          !allInjections.isEmpty
        } getOrElse false
      case _ => false//something is wrong 
    }
  }

  private def assignmentTarget(assignment: ScAssignStmt): Option[PsiAnnotationOwner with PsiElement] = {
    val l = assignment.getLExpression
    // map(x) = y check
    if (l.isInstanceOf[ScMethodCall]) None else l.asOptionOf[ScReferenceElement]
            .flatMap(_.resolve().toOption)
            .map(contextOf)
            .flatMap(_.asOptionOf[PsiAnnotationOwner with PsiElement])
    }

  private def parameterOf(argument: ScExpression): Option[PsiAnnotationOwner with PsiElement] = {
    def getParameter(methodInv: MethodInvocation, index: Int) = {
      if (index == -1) None
      else methodInv.getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression] flatMap {
        ref =>
          ref.resolve().toOption match {
            case Some(f: ScFunction) =>
              val parameters = f.parameters
              if (parameters.size == 0) None else Some(parameters.get(index.min(parameters.size - 1)))
            case Some(m: PsiMethod) =>
              val parameters = m.getParameterList.getParameters
              if (parameters.size == 0) None else parameters(index.min(parameters.size - 1)).getModifierList.toOption
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

  private def contextOf(element: PsiElement) = element match {
    case p: ScReferencePattern => p.getParent.getParent
    case field: PsiField => field.getModifierList
    case _ => element
  }
}

object ScalaLanguageInjector {
  private[this] val scalaStringLiteralManipulator = new ScalaStringLiteralManipulator 
  private[this] val safeMethodsNames = List("stripMargin", "+")
  
  def extractMultiLineStringRanges(literal: ScLiteral): mutable.MutableList[TextRange] = {
    val range = getRangeInElement(literal)

    val rangesCollected = mutable.MutableList[TextRange]()
    val extractedText = range substring literal.getText
    val margin = MultilineStringUtil.getMarginChar(literal)

    var count = 0
    val lines = new WrappedString(extractedText).lines

    for (partOfMlLine <- lines) {
      val lineLength = partOfMlLine.length
      val wsPrefixLength = partOfMlLine prefixLength (_.isWhitespace)

      if (wsPrefixLength != lineLength) {
        rangesCollected +=
          (new TextRange(if (partOfMlLine.trim startsWith margin) count + 1 + wsPrefixLength else count,
            count + lineLength) shiftRight range.getStartOffset)
      }

      count += lineLength + 1
    }
    if (rangesCollected.isEmpty) rangesCollected += new TextRange(range.getStartOffset, range.getStartOffset)

    rangesCollected
  }
  
  def handleInjectionImpl(literal: ScLiteral, language: InjectedLanguage, currentInjection: BaseInjection, 
                          list: util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]) {
    literal match {
      case multiLineString: ScLiteral if multiLineString.isMultiLineString =>
        extractMultiLineStringRanges(multiLineString) foreach {
          range => list add Trinity.create(multiLineString, language, range)
        }
      case scLiteral => currentInjection getInjectedArea scLiteral foreach {
        range => list add Trinity.create(scLiteral, language, range)
      }
    }
  }
  
  def withInjectionSupport[T](action: LanguageInjectionSupport => T) = 
    Extensions getExtensions LanguageInjectionSupport.EP_NAME find (_.getId == "scala") map action
  
  def performSimpleInjection(literals: scala.Seq[ScLiteral], injectedLanguage: InjectedLanguage, 
                             injection: BaseInjection, host: PsiElement, registrar: MultiHostRegistrar,
                             support: LanguageInjectionSupport) {
    val list = new util.ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]

    literals foreach (ScalaLanguageInjector.handleInjectionImpl(_, injectedLanguage, injection, list))

    InjectorUtils.registerInjection(injectedLanguage.getLanguage, list, host.getContainingFile, registrar)
    InjectorUtils.registerSupport(support, true, registrar)
  }
  
  def getRangeInElement(element: ScLiteral) = scalaStringLiteralManipulator getRangeInElement element
  
  def isSafeCall(testExpr: ScExpression) = testExpr match {
    case methodInv: MethodInvocation => safeMethodsNames contains methodInv.getEffectiveInvokedExpr.getText 
    case ref: ScReferenceExpression => safeMethodsNames contains ref.refName
    case _ => false
  }
}