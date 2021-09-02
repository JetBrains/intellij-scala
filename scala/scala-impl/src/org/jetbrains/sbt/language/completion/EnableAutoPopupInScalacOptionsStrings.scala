package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.withScalacOption

class EnableAutoPopupInScalacOptionsStrings extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
    withScalacOption(contextElement)(onMismatch = ThreeState.UNSURE, onMatch = _ => ThreeState.NO)
}
