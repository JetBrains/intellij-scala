package org.jetbrains.plugins.scala.util.assertions

import com.intellij.psi.{PsiDocumentManager, PsiErrorElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

trait PsiAssertions {
  def assertNoParserErrors(file: PsiFile): Unit = {
    val errorElements = file.elements.filterByType[PsiErrorElement].toSeq

    if (errorElements.nonEmpty) {
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
      val fileText = file.getText
      val errorsReadable: Seq[String] = errorElements.map { e =>
        val range = e.getTextRange
        val line = document.getLineNumber(range.getStartOffset)
        val code = fileText.substring(range.getStartOffset, range.getEndOffset)
        val errorMessage = e.getErrorDescription
        s"$line: $code - $errorMessage"
      }
      assertCollectionEquals(
        s"Expected no parser errors in file ${file.getName}",
        Seq[String](),
        errorsReadable
      )
    }
  }
}

object PsiAssertions extends PsiAssertions