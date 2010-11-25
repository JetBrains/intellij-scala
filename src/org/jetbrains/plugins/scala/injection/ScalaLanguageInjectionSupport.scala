package org.jetbrains.plugins.scala.injection

import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

class ScalaLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  private final val SUPPORT_ID: String = "scala"

  override def getId: String = {
    return SUPPORT_ID
  }

  override def getPatternClasses: Array[Class[_]] = {
    return Array(classOf[ScalaPatterns])
  }

  override def useDefaultInjector(host: PsiElement): Boolean = {
    host.isInstanceOf[ScalaPsiElement]
  }
}