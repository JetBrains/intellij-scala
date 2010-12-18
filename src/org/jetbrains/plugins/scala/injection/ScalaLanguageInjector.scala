package org.jetbrains.plugins.scala
package injection

import com.intellij.lang.injection.{MultiHostRegistrar, MultiHostInjector}
import com.intellij.psi.{PsiLanguageInjectionHost, PsiElement}
import collection.JavaConversions._
import org.intellij.plugins.intelliLang.Configuration
import java.util.ArrayList
import com.intellij.openapi.util.{Trinity, TextRange}
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import com.intellij.openapi.extensions.Extensions
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import lang.psi.api.base.ScLiteral
/**
 * Pavel Fatin
 */

class ScalaLanguageInjector(myInjectionConfiguration: Configuration) extends MultiHostInjector {
  def elementsToInjectIn = List(classOf[ScLiteral])

  def getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
    val id = host.asInstanceOf[ScLiteral].annotatedLanguageId(myInjectionConfiguration.getLanguageAnnotationClass)
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
}