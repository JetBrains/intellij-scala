package org.jetbrains.plugins.scalaDirective.lang

import com.intellij.patterns.PlatformPatterns.psiElement
import org.jetbrains.plugins.scala.lang.completion.condition
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

package object completion {
  private[scalaDirective] val DirectivePrefix = "//>"
  private[scalaDirective] val UsingDirective = "using"

  private[completion] val ScalaDirectiveKeyPattern = psiElement()
    .withElementType(ScalaDirectiveTokenTypes.tDIRECTIVE_KEY)

  private[scalaDirective] val ScalaDirectiveScalaKey = "scala"
  private[scalaDirective] val ScalaDirectiveScalaVersionPattern = psiElement()
    .withElementType(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE)
    .inside(
      psiElement(classOf[ScDirective])
        .`with`(condition[ScDirective]("scalaDirectiveWithScalaKey")(directive =>
          directive.key.exists(_.textMatches(ScalaDirectiveScalaKey))))
    )
}
