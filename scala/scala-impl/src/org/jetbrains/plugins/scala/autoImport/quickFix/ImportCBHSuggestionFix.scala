package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle

final class ImportCBHSuggestionFix private(variants: Seq[CBHSuggestionToImport], place: PsiElement)
  extends ScalaImportElementFix[CBHSuggestionToImport](place)
{
  override protected def findElementsToImport(): Seq[CBHSuggestionToImport] = variants

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] = ScalaAddImportAction.cbhSuggested(editor, variants, place)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = elements match {
    case Seq(element) => ScalaBundle.message("import.with", element.qualifiedName)
    case _            => ScalaBundle.message("import.something")
  }

  override def getFamilyName: String = ScalaBundle.message("import.compiler.suggestion")
}

object ImportCBHSuggestionFix {
  def apply(variants: Seq[CBHSuggestionToImport], place: PsiElement) = new ImportCBHSuggestionFix(variants, place)
}