package org.jetbrains.plugins.scalaDirective

import com.intellij.patterns.PlatformPatterns.psiElement
import org.jetbrains.plugins.scala.lang.completion.condition
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

package object dependencies {
  private[scalaDirective] val ScalaDirectiveDependencyKeys = Set(
    "dep", "deps", "dependencies",
    "test.dep", "test.deps", "test.dependencies",
    "compileOnly.dep", "compileOnly.deps", "compileOnly.dependencies",
  )

  private[scalaDirective] val ScalaDirectiveDependencyPattern = psiElement()
    .withElementType(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE)
    .inside(
      psiElement(classOf[ScDirective])
        .`with`(condition[ScDirective]("scalaDirectiveWithDepKey")(directive =>
          directive.key.exists(key => ScalaDirectiveDependencyKeys(key.getText))))
    )
}
