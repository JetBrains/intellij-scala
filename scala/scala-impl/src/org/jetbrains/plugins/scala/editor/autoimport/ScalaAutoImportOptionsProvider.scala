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
    if (ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS != form.isAddUnambiguousMethods) return true
    if (ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE != form.getImportOnPasteOption) return true
    if (ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY != form.isOptimizeImports) return true
    if (ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CLASSES != form.isShowPopupClasses) return true
    if (ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_STATIC_METHODS != form.isShowPopupMethods) return true
    if (ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CONVERSIONS != form.isShowPopupConversions) return true
    if (ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_IMPLICITS != form.isShowPopupImplicits) return true
    if (ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_EXTENSION_METHODS != form.isShowPopupExtensionMethods) return true
    false
  }

  override def apply(): Unit = {
    ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY = form.isAddUnambiguous
    ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS = form.isAddUnambiguousMethods
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = form.getImportOnPasteOption
    ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = form.isOptimizeImports
    ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CLASSES = form.isShowPopupClasses
    ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_STATIC_METHODS = form.isShowPopupMethods
    ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CONVERSIONS = form.isShowPopupConversions
    ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_IMPLICITS = form.isShowPopupImplicits
    ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_EXTENSION_METHODS = form.isShowPopupExtensionMethods
  }

  override def reset(): Unit = {
    form.setAddUnambiguous(ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY)
    form.setAddUnambiguousMethods(ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS)
    form.setImportOnPasteOption(ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE)
    form.setOptimizeImports(ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY)
    form.setShowPopupClasses(ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CLASSES)
    form.setShowPopupMethods(ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_STATIC_METHODS)
    form.setShowPopupConversions(ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CONVERSIONS)
    form.setShowPopupImplicits(ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_IMPLICITS)
    form.setShowPopupExtensionMethods(ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_EXTENSION_METHODS)
  }

  override def disposeUIResources(): Unit = {}
}
