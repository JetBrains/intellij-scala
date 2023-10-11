package org.jetbrains.plugins.scala.injection

import com.intellij.psi.PsiLanguageInjectionHost
import org.intellij.plugins.intelliLang.inject.{AbstractLanguageInjectionSupport, TemporaryPlacesRegistry}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.patterns.ScalaPatterns

final class ScalaLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  override def getId: String = ScalaLanguageInjectionSupport.Id

  override def isApplicableTo(host: PsiLanguageInjectionHost): Boolean = host.isInstanceOf[ScLiteral] || host.isInstanceOf[ScInfixExpr]

  override def getPatternClasses: Array[Class[_]] = Array(classOf[ScalaPatterns])

  override def useDefaultInjector(host: PsiLanguageInjectionHost): Boolean = false

  override def useDefaultCommentInjector: Boolean = false

  override def removeInjectionInPlace(host: PsiLanguageInjectionHost): Boolean = {
    if (!host.isInstanceOf[ScExpression])
      return false
    val project = host.getProject
    val registry = TemporaryPlacesRegistry.getInstance(project)
    if (registry == null)
      return false
    registry.removeHostWithUndo(project, host)
  }
}

object ScalaLanguageInjectionSupport {
  val Id = "scala"
}