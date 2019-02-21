package org.jetbrains.plugins.scala
package injection

import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiLanguageInjectionHost
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport

/**
 * Pavel Fatin
 */
final class ScalaLanguageInjectionSupport extends AbstractLanguageInjectionSupport {

  override def getId = "scala"

  override def getPatternClasses = Array(classOf[ScalaLanguageInjectionSupport.Patterns])

  override def useDefaultInjector(host: PsiLanguageInjectionHost) = false
}

object ScalaLanguageInjectionSupport {

  final class Patterns extends PsiJavaPatterns
}