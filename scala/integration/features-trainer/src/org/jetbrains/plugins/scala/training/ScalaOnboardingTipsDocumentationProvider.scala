package org.jetbrains.plugins.scala.training

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import training.onboarding.AbstractOnboardingTipsDocumentationProvider

final class ScalaOnboardingTipsDocumentationProvider extends AbstractOnboardingTipsDocumentationProvider(ScalaTokenTypes.tLINE_COMMENT) {
  override def isLanguageFile(psiFile: PsiFile): Boolean = psiFile.isScala2File || psiFile.isScala3File
}
