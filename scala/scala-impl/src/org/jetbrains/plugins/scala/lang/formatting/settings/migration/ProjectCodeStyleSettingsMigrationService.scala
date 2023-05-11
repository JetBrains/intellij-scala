package org.jetbrains.plugins.scala.lang.formatting.settings.migration

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.components.{RoamingType, Service, State, Storage, StoragePathMacros}
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleScheme
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.migration.CodeStyleSettingsMigrationServiceBase.MigrationItem

@State(
  name = "ProjectCodeStyleSettingsMigration",
  storages = Array[Storage](new Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED))
)
@Service(Array(Service.Level.PROJECT))
final class ProjectCodeStyleSettingsMigrationService(private val project: Project)
  extends CodeStyleSettingsMigrationServiceBase  {

  def init(): Unit = {
    migrateIfNeeded()

    //application-level code style settings should be migrated at some point in time
    //due to ApplicationComponent is deprecated this will be done on first project opening
    ApplicationCodeStyleSettingsMigrationService.instance.migrateIfNeeded()
  }

  override protected def migrate(migrations: Seq[MigrationItem], currentVersion: Int, latestVersion: Int): Unit = {
    val codeStyleSchemesModel = new CodeStyleSchemesModel(project)
    val projectScheme: CodeStyleScheme = codeStyleSchemesModel.getProjectScheme
    val scalaSettings = projectScheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    migrations.foreach(_.migrate(scalaSettings))

    Log.info(s"Migrated project-level scala code style settings from version $currentVersion to $latestVersion")

    codeStyleSchemesModel.apply()
  }
}
