package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.components.ProjectComponent
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl
import org.jetbrains.plugins.scala.ScalaLanguage

/**
  * @author Roman.Shein
  *         Date: 24.01.2017
  */
class LegacyCodeStyleSettingsComponent extends ProjectComponent {
  override def projectClosed(): Unit = {}

  override def projectOpened(): Unit = {
    import collection.JavaConversions._
    for (scheme: CodeStyleScheme <- CodeStyleSchemesImpl.getSchemeManager.getAllSchemes) {
      val scalaSettings = scheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      val commonSettings = scheme.getCodeStyleSettings.getCommonSettings(ScalaLanguage.INSTANCE)
      if (scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN == ScalaCodeStyleSettings.NO_NEW_LINE &&
        commonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE) {
        commonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false
        scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = ScalaCodeStyleSettings.NEW_LINE_ALWAYS
      }
    }
  }

  override def initComponent(): Unit = {}

  override def disposeComponent(): Unit = {}

  override def getComponentName: String = "LegacyCodeStyleSettingsComponent"
}
