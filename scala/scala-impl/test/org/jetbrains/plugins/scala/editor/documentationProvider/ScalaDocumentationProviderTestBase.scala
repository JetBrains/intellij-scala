package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.documentationProvider.base.DocumentationProviderTestBase

abstract class ScalaDocumentationProviderTestBase extends DocumentationProviderTestBase {

  override protected def documentationProvider = new ScalaDocumentationProvider

  override protected def createFile(fileContent: String): PsiFile =
    myFixture.configureByText(ScalaFileType.INSTANCE, fileContent)
}
