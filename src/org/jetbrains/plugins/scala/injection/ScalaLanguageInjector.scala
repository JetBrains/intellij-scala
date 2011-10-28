package org.jetbrains.plugins.scala
package injection

import com.intellij.lang.injection.{MultiHostRegistrar, MultiHostInjector}
import collection.JavaConversions._
import org.intellij.plugins.intelliLang.Configuration
import java.util.ArrayList
import com.intellij.openapi.util.{Trinity, TextRange}
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import com.intellij.openapi.extensions.Extensions
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import lang.psi.api.expr._
import lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScVariableDefinition}
import com.intellij.psi._
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.base.{ScReferenceElement, ScLiteral}
import lang.psi.ScalaPsiUtil.readAttribute
import org.jetbrains.plugins.scala.extensions._
import lang.formatting.settings.ScalaCodeStyleSettings

/**
 * Pavel Fatin
 */

class ScalaLanguageInjector(myInjectionConfiguration: Configuration) extends MultiHostInjector {
  override def elementsToInjectIn = List(classOf[ScLiteral], classOf[ScInfixExpr])

  override def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
    if (ScalaCodeStyleSettings.getInstance(host.getProject).DISABLE_LANGUAGE_INJECTION) return

    val literals = literalsOf(host)

    if (literals.isEmpty) return

    if (injectUsingAnnotation(registrar, host, literals)) return

    injectUsingPatterns(registrar, host, literals)
  }

  private def literalsOf(host: PsiElement): Seq[ScLiteral] = {
    if(host.getParent.isInstanceOf[ScInfixExpr])
      return Seq.empty // process top-level expressions only

    val expressions = host.depthFirst.filter(_.isInstanceOf[ScExpression]).toList

    val suitable = expressions.forall(_ match {
      case l: ScLiteral if l.isString => true
      case r: ScReferenceExpression if r.getText == "+" => true
      case _: ScInfixExpr => true
      case _ => false
    })

    if(suitable)
      expressions.collect {
        case x: ScLiteral if x.isString => x
      }
    else
      Seq.empty
  }

  def injectUsingAnnotation(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]): Boolean = {
    Configuration.getInstance().getDfaOption match {
      case Configuration.DfaOption.OFF => return false
      case _ =>
    }
    val expression = host.asInstanceOf[ScExpression]
    // TODO implicit conversion checking (SCL-2599), disabled (performance reasons)
    val annotationOwner = annotationOwnerFor(expression) //.orElse(implicitAnnotationOwnerFor(literal))

    val annotation = annotationOwner.flatMap(_.getAnnotations.find(
      _.getQualifiedName == myInjectionConfiguration.getLanguageAnnotationClass))

    val languageId = annotation.flatMap(readAttribute(_, "value"))
    val language = languageId.flatMap(it => InjectedLanguage.findLanguageById(it).toOption)

    language.foreach { it =>
      registrar.startInjecting(it)
      literals.zipWithIndex.foreach { p =>
        val (literal, i) = p
        val prefix = if(i == 0) annotation.flatMap(readAttribute(_, "prefix")).mkString else ""
        val suffix = if(i == literals.size - 1) annotation.flatMap(readAttribute(_, "suffix")).mkString else ""
        registrar.addPlace(prefix, suffix, literal.asInstanceOf[PsiLanguageInjectionHost],
          ScalaStringLiteralManipulator.getLiteralRange(literal.getText))
      }
      registrar.doneInjecting()
    }

    language.isDefined
  }

  def injectUsingPatterns(registrar: MultiHostRegistrar, host: PsiElement, literals: scala.Seq[ScLiteral]) {
    Extensions.getExtensions(LanguageInjectionSupport.EP_NAME).find(_.getId == "scala").foreach { support =>
      val injections = myInjectionConfiguration.getInjections(support.getId).toIterator

      var done = false

      while(!done && injections.hasNext) {
        val injection = injections.next()

        if(injection.acceptsPsiElement(host)) {
          val language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId)
          if (language != null) {
            val injectedLanguage = InjectedLanguage.create(injection.getInjectedLanguageId, injection.getPrefix, injection.getSuffix, false)
            val list = new ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]
            literals.foreach { literal =>
              for (range <- injection.getInjectedArea(literal)) {
                list.add(Trinity.create(literal, injectedLanguage, range))
              }
            }
            InjectorUtils.registerInjection(language, list, host.getContainingFile, registrar)
            InjectorUtils.registerSupport(support, true, registrar)
          }
          done = true
        }
      }
    }
  }

  def annotationOwnerFor(literal: ScExpression): Option[PsiAnnotationOwner] = literal.getParent match {
    case pattern: ScPatternDefinition => Some(pattern)
    case variable: ScVariableDefinition => Some(variable)
    case _: ScArgumentExprList => parameterOf(literal)
    case assignment: ScAssignStmt => assignmentTarget(assignment)
    case _ => None
  }

  def implicitAnnotationOwnerFor(literal: ScLiteral): Option[PsiAnnotationOwner] = {
    literal.getImplicitConversions()._2.flatMap(_.asOptionOf[ScFunction]).flatMap(_.parameters.headOption)
  }

  private def assignmentTarget(assignment: ScAssignStmt): Option[PsiAnnotationOwner] = {
    val l = assignment.getLExpression
    // map(x) = y check
    if (l.isInstanceOf[ScMethodCall]) None else l.asOptionOf[ScReferenceElement]
            .flatMap(_.resolve().toOption)
            .map(contextOf)
            .flatMap(_.asOptionOf[PsiAnnotationOwner])
    }

  private def parameterOf(argument: ScExpression): Option[PsiAnnotationOwner] = argument.getParent match {
    case args: ScArgumentExprList => {
      val index = args.exprs.indexOf(argument)
      if(index == -1) None else {
        args.getParent match {
          case call: ScMethodCall => {
            call.getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression].flatMap { ref =>
              ref.resolve().toOption match {
                case Some(f: ScFunction) => {
                  val parameters = f.parameters
                  if(parameters.size == 0) None else Some(parameters.get(index.min(parameters.size - 1)))
                }
                case Some(m: PsiMethod) => {
                  val parameters = m.getParameterList.getParameters
                  if(parameters.size == 0) None else parameters(index.min(parameters.size - 1)).getModifierList.toOption
                }
                case _ => None
              }
            }
          }
          case _ => None
        }
      }
    }
    case _ => None
  }

  private def contextOf(element: PsiElement) = element match {
    case p: ScReferencePattern => p.getParent.getParent
    case field: PsiField => field.getModifierList
    case _ => element
  }
}