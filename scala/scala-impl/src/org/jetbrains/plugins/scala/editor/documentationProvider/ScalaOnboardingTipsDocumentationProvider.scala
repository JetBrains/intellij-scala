package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions._
import training.onboarding.AbstractOnboardingTipsDocumentationProvider
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

class ScalaOnboardingTipsDocumentationProvider extends AbstractOnboardingTipsDocumentationProvider(ScalaTokenTypes.tLINE_COMMENT) {
  override def isLanguageFile(psiFile: PsiFile): Boolean = psiFile.isScala2File || psiFile.isScala3File
}
