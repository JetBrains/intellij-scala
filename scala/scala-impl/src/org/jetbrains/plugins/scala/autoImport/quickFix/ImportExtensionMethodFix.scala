package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class ImportExtensionMethodFix(ref: ScReferenceExpression,
                               computation: ConversionToImportComputation) extends ScalaImportElementFix[ExtensionMethodToImport](ref) {
  override protected def findElementsToImport(): Seq[ExtensionMethodToImport] = computation.extensionMethods

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importExtensionMethod(editor, elements, ref)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = elements match {
    case Seq(extensionMethod) => ScalaBundle.message("import.with", extensionMethod.qualifiedName)
    case _ => getFamilyName
  }

  override def getFamilyName: String = ScalaBundle.message("import.extension.method")

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() && ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_EXTENSION_METHODS
}

object ImportExtensionMethodFix {
  def apply(ref: ScReferenceExpression, computation: ConversionToImportComputation) =
    new ImportExtensionMethodFix(ref, computation)
}
