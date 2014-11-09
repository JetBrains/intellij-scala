package org.jetbrains.plugins.scala.injection

import com.intellij.psi.PsiLanguageInjectionHost
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport

/**
 * Pavel Fatin
 */

class ScalaLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  private final val SUPPORT_ID: String = "scala"

  override def getId: String = SUPPORT_ID

  override def getPatternClasses: Array[Class[_]] = Array(classOf[ScalaPatterns])

  override def useDefaultInjector(host: PsiLanguageInjectionHost): Boolean = false
}