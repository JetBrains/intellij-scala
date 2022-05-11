package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType

abstract class ScalaDocumentationProviderTestBase extends DocumentationProviderTestBase
 with ScalaDocumentationsSectionsTesting {

  override protected def documentationProvider = new ScalaDocumentationProvider

  override protected def createFile(fileContent: String): PsiFile =
    myFixture.configureByText(ScalaFileType.INSTANCE, fileContent)
}
