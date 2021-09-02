package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.{scalacOptionByFlag, withScalacOption}

class SbtScalacOptionsDocumentationProvider extends AbstractDocumentationProvider {
  override def generateDoc(element: PsiElement, originalElement: PsiElement): String =
    element match {
      case SbtScalacOptionDocHolder(option) =>
        generateScalacOptionDoc(option)
      case _ => null
    }

  /**
   * If contextElement is a string corresponding to a scalac option, wrap this option in [[SbtScalacOptionDocHolder]],
   * otherwise return null
   */
  override def getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement,
                                             targetOffset: Int): PsiElement =
    contextElement match {
      case null => null
      case _ => withScalacOption(contextElement)(onMismatch = null, onMatch = wrapInDocHolder)
    }

  private def wrapInDocHolder(str: ScStringLiteral): PsiElement =
    scalacOptionByFlag.get(str.getValue)
      .map(SbtScalacOptionDocHolder(_)(str.getProject))
      .orNull

  private def generateScalacOptionDoc(option: SbtScalacOptionInfo): String =
    option.description

}
