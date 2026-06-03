package org.jetbrains.plugins.scala.lang.formatting.settings.inference

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.startup.ProjectActivity

import scala.jdk.CollectionConverters._

//todo: Revalidate, add tests and remove
private final class LegacyCodeStyleSettingsComponent extends ProjectActivity {
  override def execute(project: Project): Unit = {
    // Application-level services are initialised exactly once.
    ApplicationManager.getApplication.getService(classOf[LegacyCodeStyleSettingsComponent.AppService])
  }
}

private object LegacyCodeStyleSettingsComponent {
  @Service(Array(Service.Level.APP))
  private final class AppService {
    // Executed in the service constructor.
    setupCodeStyleSchemes()

    private def setupCodeStyleSchemes(): Unit = {
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
  }
}
