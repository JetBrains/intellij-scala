package org.jetbrains.plugins.scala
package worksheet

import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.{FileViewProvider, PsiFile}
import org.jetbrains.plugins.scala.lang.psi._

//noinspection TypeAnnotation
final class WorksheetParserDefinition extends lang.parser.ScalaParserDefinition {

  override val getFileNodeType = new stubs.elements.ScStubFileElementType(WorksheetLanguage.INSTANCE) {

    override def getExternalId = "scala.worksheet.file"

    override def getBuilder = new ScFileStubBuilderImpl {
      override protected def createStubForFile(file: PsiFile) = new PsiFileStubImpl(file)
    }
  }

  override def createFile(viewProvider: FileViewProvider) =
    new impl.ScalaFileImpl(viewProvider, WorksheetFileType) {
      override def isWorksheetFile: Boolean = true
    }
}