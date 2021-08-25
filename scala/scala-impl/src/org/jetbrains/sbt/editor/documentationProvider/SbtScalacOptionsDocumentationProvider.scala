package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.SCALAC_OPTIONS_DOC_KEY

class SbtScalacOptionsDocumentationProvider extends AbstractDocumentationProvider {
  override def generateDoc(element: PsiElement, originalElement: PsiElement): String =
    if (element == null) null else element.getUserData(SCALAC_OPTIONS_DOC_KEY)
}
