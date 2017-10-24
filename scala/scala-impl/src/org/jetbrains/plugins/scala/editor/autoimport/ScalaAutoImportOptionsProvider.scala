package org.jetbrains.plugins.scala.editor.autoimport

import javax.swing.JComponent

import com.intellij.application.options.editor.AutoImportOptionsProvider
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * @author Alefas
 * @since 24.05.12
 */

class ScalaAutoImportOptionsProvider extends AutoImportOptionsProvider {

  private var form: ScalaAutoImportOptionsProviderForm = _

  override def createComponent(): JComponent = {
    form = new ScalaAutoImportOptionsProviderForm()
    form.getComponent
  }

  override def isModified: Boolean = {
    if (ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY != form.isAddUnambiguous) return true
    if (ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE != form.getImportOnPasteOption) return true
    if (ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY != form.isOptimizeImports) return true
    false
  }

  override def apply(): Unit = {
    ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY = form.isAddUnambiguous
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = form.getImportOnPasteOption
    ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = form.isOptimizeImports
  }

  override def reset(): Unit = {
    form.setAddUnambiguous(ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY)
    form.setImportOnPasteOption(ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE)
    form.setOptimizeImports(ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY)
  }

  override def disposeUIResources(): Unit = {}
}
