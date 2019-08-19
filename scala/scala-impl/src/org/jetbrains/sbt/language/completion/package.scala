package org.jetbrains.sbt
package language

import com.intellij.patterns.PlatformPatterns.{psiElement, psiFile}
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.StandardPatterns.instanceOf
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

package object completion {

  def sbtFilePattern: Capture[PsiElement] = psiElement.inFile {
    psiFile.withFileType(instanceOf(SbtFileType.getClass))
  }

  def infixExpressionChildPattern: Capture[PsiElement] = psiElement.withSuperParent(2, classOf[ScInfixExpr])
}
