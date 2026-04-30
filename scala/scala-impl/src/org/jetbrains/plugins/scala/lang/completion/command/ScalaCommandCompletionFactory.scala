package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.CommandCompletionFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

//noinspection ApiStatus,UnstableApiUsage
final class ScalaCommandCompletionFactory extends CommandCompletionFactory with DumbAware {
  override def isApplicable(psiFile: PsiFile, offset: Int): Boolean = psiFile match {
    case file: ScalaFile =>
      !isInsideStringLiteral(file, offset)
    case _ => false
  }

  // TODO(SCL-24924): disabled in injected code for now
  override def isApplicableForHost(psiFile: PsiFile, offset: Int): Boolean = false

  override def createFile(originalFile: PsiFile, text: String): PsiFile = {
    val originalVirtualFile = originalFile.getVirtualFile
    val newFile = ScalaPsiElementFactory.createScalaFileFromText(text, features = originalFile)(ctx = originalFile)

    if (originalVirtualFile != null) {
      newFile.getVirtualFile match {
        case newVirtualFile: LightVirtualFile =>
          newVirtualFile.setOriginalFile(originalVirtualFile)
          newVirtualFile.setFileType(originalVirtualFile.getFileType)
        case _ =>
      }
    }

    newFile
  }

  private def isInsideStringLiteral(file: ScalaFile, offset: Int): Boolean = {
    file.findElementAt(offset) match {
      case null => false
      case element =>
        val elementType = element.elementType
        val elementTypeMatches = ScalaTokenTypes.STRING_LITERAL_TOKEN_SET.contains(elementType)
        elementTypeMatches && element.getTextRange.containsOffset(offset)
    }
  }
}
