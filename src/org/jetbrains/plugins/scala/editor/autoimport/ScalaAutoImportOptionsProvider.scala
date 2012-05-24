package org.jetbrains.plugins.scala.editor.autoimport

import com.intellij.application.options.editor.AutoImportOptionsProvider
import javax.swing.JPanel
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings

/**
 * @author Alefas
 * @since 24.05.12
 */

class ScalaAutoImportOptionsProvider extends AutoImportOptionsProvider {
  private var form: ScalaAutoImportOptionsProviderForm = null

  def createComponent() = {
    form = new ScalaAutoImportOptionsProviderForm()
    form.getComponent
  }

  def isModified: Boolean = {
    if (ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY != form.isAddUnambiguous)
      return true
    if (ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE != form.getImportOnPasteOption)
      return true
    /*if (ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP != form.isShowImportPopup)
      return true*/
    if (DaemonCodeAnalyzerSettings.getInstance().SHOW_ADD_IMPORT_HINTS != form.isShowImportPopup)
      return true
    false
  }

  def apply() {
    ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY = form.isAddUnambiguous
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = form.getImportOnPasteOption
//    ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP = form.isShowImportPopup
    DaemonCodeAnalyzerSettings.getInstance().SHOW_ADD_IMPORT_HINTS = form.isShowImportPopup
  }

  def reset() {
    form.setAddUnambiguous(ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY)
    form.setImportOnPasteOption(ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE)
//    form.setShowImportPopup(ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP)
    form.setShowImportPopup(DaemonCodeAnalyzerSettings.getInstance().SHOW_ADD_IMPORT_HINTS)
  }

  def disposeUIResources() {}
}
