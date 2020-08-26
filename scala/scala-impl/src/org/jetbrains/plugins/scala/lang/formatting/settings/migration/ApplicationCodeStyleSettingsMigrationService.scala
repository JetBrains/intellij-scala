package org.jetbrains.plugins.scala.lang.formatting.settings.migration

import com.intellij.openapi.components.{ServiceManager, State, Storage}
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.migration.CodeStyleSettingsMigrationServiceBase.MigrationItem
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.jdk.CollectionConverters._

@State(
  name = "ApplicationCodeStyleSettingsMigration",
  storages = Array[Storage](new Storage(ScalaApplicationSettings.STORAGE_FILE_NAME))
)
class ApplicationCodeStyleSettingsMigrationService extends CodeStyleSettingsMigrationServiceBase {

  override protected def migrate(migrations: Seq[MigrationItem], currentVersion: Int, latestVersion: Int): Unit = {
    val appLevelSchemes: Seq[CodeStyleScheme] = CodeStyleSchemesImpl.getSchemeManager.getAllSchemes.asScala.toSeq

    appLevelSchemes.zipWithIndex.foreach { case (scheme, idx) =>
      val scalaSettings = scheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      migrations.foreach(_.migrate(scalaSettings))
      Log.info(s"Migrated installation-level scala code style settings ($idx) from version $currentVersion to $latestVersion")
    }
  }
}

object ApplicationCodeStyleSettingsMigrationService {

  def instance: ApplicationCodeStyleSettingsMigrationService =
    ServiceManager.getService(classOf[ApplicationCodeStyleSettingsMigrationService])
}
