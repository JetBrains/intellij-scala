package org.jetbrains.plugins.scala.lang

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

object TokenTexts {
  def importWildcardText(context: PsiElement): String =
    text(context, "*", "_")


  private def text(context: PsiElement, inScala3Source3: String, inScala2: String): String =
    if (context.isScala3OrSource3Enabled) inScala3Source3 else inScala2
}
