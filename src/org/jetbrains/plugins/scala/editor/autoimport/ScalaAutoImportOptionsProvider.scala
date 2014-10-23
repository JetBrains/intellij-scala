package org.jetbrains.plugins.scala.editor.autoimport

import com.intellij.application.options.editor.AutoImportOptionsProvider
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

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
    if (ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY != form.isAddUnambiguous) return true
    if (ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE != form.getImportOnPasteOption) return true
    if (ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY != form.isOptimizeImports) return true
    false
  }

  def apply() {
    ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY = form.isAddUnambiguous
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = form.getImportOnPasteOption
    ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = form.isOptimizeImports
  }

  def reset() {
    form.setAddUnambiguous(ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY)
    form.setImportOnPasteOption(ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE)
    form.setOptimizeImports(ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY)
  }

  def disposeUIResources() {}
}
