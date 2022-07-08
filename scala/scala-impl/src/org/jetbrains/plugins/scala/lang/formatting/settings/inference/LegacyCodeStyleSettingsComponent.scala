package org.jetbrains.plugins.scala.lang.formatting.settings.inference

import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.components.RunOnceStartupActivity
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

import scala.jdk.CollectionConverters._

//todo: Revalidate, add tests and remove
final class LegacyCodeStyleSettingsComponent extends RunOnceStartupActivity {

  override def doRunActivity(): Unit = {
    val codeStyleSchemes = CodeStyleSchemesImpl.getSchemeManager.getAllSchemes.asScala
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

  override protected def doCleanup(): Unit = {}
}
