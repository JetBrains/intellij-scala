package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.command.CompletionCommandKt.getCommandContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.annotations.{NotNull, Nullable}

//noinspection UnstableApiUsage
package object command {
  @Nullable
  def getNonWhitespaceCommandContext(@NotNull file: PsiFile, offset: Int): PsiElement =
    getCommandContext(offset, file) match {
      case ws: PsiWhiteSpace => PsiTreeUtil.prevVisibleLeaf(ws)
      case ctx => ctx
    }
}
