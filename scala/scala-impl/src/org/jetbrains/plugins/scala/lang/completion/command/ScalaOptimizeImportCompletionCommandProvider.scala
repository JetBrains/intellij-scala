package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractOptimizeImportCompletionCommandProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil

//noinspection UnstableApiUsage
final class ScalaOptimizeImportCompletionCommandProvider extends AbstractOptimizeImportCompletionCommandProvider {
  override def isImportList(psiFile: PsiFile, offset: Int): Boolean =
    if (offset < 1) false
    else {
      val element = psiFile.findElementAt(offset - 1)
      ScalaPsiUtil.isInsideImportStatement(element)
    }

  // Used in highlighting. Don't highlight anything as Scala can have multiple "import lists" including local ones
  override def getTextRangeImportList(psiFile: PsiFile, offset: Int): TextRange = null
}
