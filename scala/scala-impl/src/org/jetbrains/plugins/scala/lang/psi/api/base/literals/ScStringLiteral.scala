package org.jetbrains.plugins.scala.lang.psi.api.base.literals

import com.intellij.psi.{ContributedReferenceHost, PsiLanguageInjectionHost}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

trait ScStringLiteral extends ScLiteral
  with PsiLanguageInjectionHost
  with ContributedReferenceHost {

  override protected type V = String
}

object ScStringLiteral {
  def unapply(lit: ScStringLiteral): Option[String] = Option(lit.getValue)
}