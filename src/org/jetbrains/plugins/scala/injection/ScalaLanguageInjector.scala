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
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.expr._
import lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScVariableDefinition}
import lang.psi.api.statements.params.ScParameter
import lang.psi.api.base.{ScPrimaryConstructor, ScConstructor, ScReferenceElement, ScLiteral}
import com.intellij.psi._

/**
 * Pavel Fatin
 */

class ScalaLanguageInjector(myInjectionConfiguration: Configuration) extends MultiHostInjector {
  def elementsToInjectIn = List(classOf[ScLiteral])

  def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
    val id = annotatedLanguageId(host.asInstanceOf[ScLiteral], myInjectionConfiguration.getLanguageAnnotationClass)
    val language = id.flatMap(it => InjectedLanguage.findLanguageById(it).toOption)

    language.foreach{it =>
      registrar.startInjecting(it)
      registrar.addPlace("", "", host.asInstanceOf[PsiLanguageInjectionHost],
        ScalaStringLiteralManipulator.getLiteralRange(host.asInstanceOf[ScLiteral].getText))
      registrar.doneInjecting()
    }

    if (language.isDefined) return

    var done = false

    Extensions.getExtensions(LanguageInjectionSupport.EP_NAME).find(_.getId == "scala").foreach { support =>
      myInjectionConfiguration.getInjections(support.getId)
              .view.takeWhile(_ => !done).filter(_.acceptsPsiElement(host)).foreach { injection =>
        val language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId)
        if (language != null) {
          val injectedLanguage = InjectedLanguage.create(injection.getInjectedLanguageId, injection.getPrefix, injection.getSuffix, false)
          val list = new ArrayList[Trinity[PsiLanguageInjectionHost, InjectedLanguage, TextRange]]
          for (range <- injection.getInjectedArea(host)) {
            list.add(Trinity.create(host.asInstanceOf[PsiLanguageInjectionHost], injectedLanguage, range))
          }
          InjectorUtils.registerInjection(language, list, host.getContainingFile, registrar)
          InjectorUtils.registerSupport(support, true, registrar)
          done = true
        }
      }
    }
  }

  def annotatedLanguageId(literal: ScLiteral, languageAnnotationName: String): Option[String] = {
    val annotationOwner = literal.getParent match {
      case pattern: ScPatternDefinition => Some(pattern)
      case variable: ScVariableDefinition => Some(variable)
      case _: ScArgumentExprList => parameterOf(literal)
      case assignment: ScAssignStmt => assignmentTarget(assignment)
      case _ => None
    }
    annotationOwner.flatMap(extractLanguage(_, languageAnnotationName))
  }

  private def assignmentTarget(assignment: ScAssignStmt): Option[PsiAnnotationOwner] = {
    val l = assignment.getLExpression
    // map(x) = y check
    if (l.isInstanceOf[ScMethodCall]) None else l.asOptionOf(classOf[ScReferenceElement])
            .flatMap(_.resolve.toOption)
            .map(contextOf)
            .flatMap(_.asOptionOf(classOf[PsiAnnotationOwner]))
    }

  private def parameterOf(argument: ScExpression): Option[PsiAnnotationOwner] = argument.getParent match {
    case args: ScArgumentExprList => {
      val index = args.exprs.indexOf(argument)
      if(index == -1) None else {
        args.getParent match {
          case call: ScMethodCall => {
            call.getInvokedExpr.asOptionOf(classOf[ScReferenceExpression]).flatMap { ref =>
              ref.resolve.toOption match {
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

  private def extractLanguage(element: PsiAnnotationOwner, languageAnnotationName: String) = {
    element.getAnnotations
            .find(_.getQualifiedName == languageAnnotationName)
            .flatMap(_.findAttributeValue("value").toOption)
            .flatMap(_.asOptionOf(classOf[PsiLiteral]))
            .map(_.getValue.toString)
            .flatMap(_.asOptionOf(classOf[String]))
  }
}