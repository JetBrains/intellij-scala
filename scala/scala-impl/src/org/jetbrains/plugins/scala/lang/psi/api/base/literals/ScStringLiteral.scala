package org.jetbrains.plugins.scala.lang.psi.api.base.literals

import org.jetbrains.plugins.scala.lang.psi.api._
import com.intellij.psi.{ContributedReferenceHost, PsiLanguageInjectionHost}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScLiteralBase}

trait ScStringLiteralBase extends ScLiteralBase with PsiLanguageInjectionHost with ContributedReferenceHost { this: ScStringLiteral =>

  override protected type V = String
}

abstract class ScStringLiteralCompanion {
  def unapply(lit: ScStringLiteral): Option[String] = Option(lit.getValue)
}