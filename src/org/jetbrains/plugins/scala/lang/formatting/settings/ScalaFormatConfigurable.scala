package org.jetbrains.plugins.scala.lang.formatting.settings

import icons.Icons
import com.intellij.application.options.{CodeStyleAbstractConfigurable, CodeStyleAbstractPanel}
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
 
class ScalaFormatConfigurable(settings: CodeStyleSettings, originalSettings: CodeStyleSettings)
  extends CodeStyleAbstractConfigurable(settings, originalSettings, ScalaBundle.message("title.scala.settings")) {
    protected def createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = new ScalaCodeStylePanel(settings)
    override def getIcon = Icons.FILE_TYPE_LOGO
    def getHelpTopic: String = null
}