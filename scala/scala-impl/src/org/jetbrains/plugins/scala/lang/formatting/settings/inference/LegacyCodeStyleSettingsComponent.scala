package org.jetbrains.plugins.scala.lang.formatting.settings.inference

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

import scala.collection.JavaConverters._

/**
  * @author Roman.Shein
  *         Date: 24.01.2017
  */
class LegacyCodeStyleSettingsComponent(project: Project) extends ProjectComponent {

  override def projectOpened(): Unit = {
    val codeStyleSchemes: Seq[CodeStyleScheme] = CodeStyleSchemesImpl.getSchemeManager.getAllSchemes.asScala
    codeStyleSchemes.foreach { scheme =>
      val scalaSettings = scheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      val commonSettings = scheme.getCodeStyleSettings.getCommonSettings(ScalaLanguage.INSTANCE)
      if (commonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE &&
        scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN == ScalaCodeStyleSettings.NO_NEW_LINE
      ) {
        commonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false
        scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN = ScalaCodeStyleSettings.NEW_LINE_ALWAYS
      }
    }
  }

  override def getComponentName: String = "LegacyCodeStyleSettingsComponent"
}
