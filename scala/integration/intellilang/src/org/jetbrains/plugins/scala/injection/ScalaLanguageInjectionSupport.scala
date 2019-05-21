package org.jetbrains.plugins.scala
package injection

import com.intellij.psi.PsiLanguageInjectionHost
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.patterns.ScalaPatterns

final class ScalaLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  override def getId: String = "scala"

  override def isApplicableTo(host: PsiLanguageInjectionHost): Boolean = host.is[ScLiteral, ScInfixExpr]

  override def getPatternClasses: Array[Class[_]] = Array(classOf[ScalaPatterns])

  override def useDefaultInjector(host: PsiLanguageInjectionHost): Boolean = false

  override def useDefaultCommentInjector: Boolean = false
}