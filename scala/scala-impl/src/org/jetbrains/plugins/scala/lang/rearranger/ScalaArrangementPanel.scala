package org.jetbrains.plugins.scala
package lang.rearranger

import com.intellij.application.options.codeStyle.arrangement.ArrangementSettingsPanel
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * @author Roman.Shein
 * Date: 15.07.13
 */
class ScalaArrangementPanel(settings: CodeStyleSettings) extends ArrangementSettingsPanel(settings, ScalaLanguage.INSTANCE) {

    override protected def getRightMargin: Int = 80

    override protected def getFileType: ScalaFileType = ScalaFileType.INSTANCE

    protected def getPreviewText: String = null

}
