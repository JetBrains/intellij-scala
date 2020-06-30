package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.options.{BeanConfigurable, SearchableConfigurable}
import com.intellij.ui.IdeBorderFactory
import javax.swing._
import org.jetbrains.plugins.scala.ScalaBundle

class ScalaEditorSmartKeysConfigurable extends BeanConfigurable[ScalaApplicationSettings](ScalaApplicationSettings.getInstance) with SearchableConfigurable {
  override def getId: String = "ScalaSmartKeys"
  //noinspection ScalaExtractStringToBundle
  override def getDisplayName: String = "Scala"
  override def getHelpTopic: String = null
  override def enableSearch(option: String): Runnable = null
  override def disposeUIResources(): Unit = super.disposeUIResources()

  init()

  def init(): Unit = {
    val settings: ScalaApplicationSettings = getInstance();
    checkBox(ScalaBundle.message("insert.pair.multiline.quotes"), () => settings.INSERT_MULTILINE_QUOTES, settings.INSERT_MULTILINE_QUOTES = _)
    checkBox(ScalaBundle.message("wrap.single.expression.body"), () => settings.WRAP_SINGLE_EXPRESSION_BODY, settings.WRAP_SINGLE_EXPRESSION_BODY = _)
    checkBox(ScalaBundle.message("insert.and.remove.block.braces.automatically.based.on..."), () => settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY, settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY = _)
    checkBox(ScalaBundle.message("upgrade.to.interpolated"), () => settings.UPGRADE_TO_INTERPOLATED, settings.UPGRADE_TO_INTERPOLATED = _)
  }

  override def createComponent: JComponent = {
    val result = super.createComponent
    assert(result != null, "ScalaEditorSmartKeysConfigurable panel was not created")
    //noinspection ScalaExtractStringToBundle
    result.setBorder(IdeBorderFactory.createTitledBorder("Scala"))
    result
  }
}