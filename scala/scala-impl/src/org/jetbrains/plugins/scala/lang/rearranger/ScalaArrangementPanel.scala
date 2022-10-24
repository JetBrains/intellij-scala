package org.jetbrains.plugins.scala.lang.rearranger

import com.intellij.application.options.codeStyle.arrangement.ArrangementSettingsPanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

class ScalaArrangementPanel(settings: CodeStyleSettings) extends ArrangementSettingsPanel(settings, ScalaLanguage.INSTANCE) {

    override protected def getRightMargin: Int = 80

    override protected def getFileType: ScalaFileType = ScalaFileType.INSTANCE

    override protected def getPreviewText: String = null
}
