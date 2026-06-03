package org.jetbrains.plugins.scala.lang.formatting.settings.migration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components._
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.migration.CodeStyleSettingsMigrationServiceBase._

import scala.beans.BeanProperty

/**
 * This service is used to migrate existing code style settings.
 * It is done for existing projects and IntelliJ installation-level code style settings.
 * Not that code style file itself does not have any version field.
 * If a user didn't change any default settings all the migration of old settings to the new settings
 * should result settings with default values defined in [[ScalaCodeStyleSettings]].
 */
abstract class CodeStyleSettingsMigrationServiceBase extends PersistentStateComponent[MyState] {

  protected val Log: Logger = Logger.getInstance(this.getClass)

  private var state: MyState = new MyState
  override def getState: MyState = this.state
  override def loadState(state: MyState): Unit = this.state = state

  protected def migrate(migrations: Seq[MigrationItem], currentVersion: Int, latestVersion: Int): Unit

  private[migration]
  final def migrateIfNeeded(): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) return

    val currentVersion = getState.version
    val latestVersion = Migrations.all.map(_.version).max

    if (latestVersion > currentVersion) {
      val unappliedMigrations = Migrations.all.filter(_.version > currentVersion)
      migrate(unappliedMigrations, currentVersion, latestVersion)
      loadState(new MyState(latestVersion))
    }
  }
}

object CodeStyleSettingsMigrationServiceBase {
  class MyState(@BeanProperty var version: Int) {
    def this() = this(0) // IntelliJ platform requires the default constructor
  }

  private[migration]
  case class MigrationItem(version: Int, migrate: ScalaCodeStyleSettings => Unit)

  //noinspection ScalaDeprecation
  private[migration]
  object Migrations {
    // NOTE: these migrations are effectively unused after SCL-23809, but we still keep them just for the history.
    // For the future newly added migrations you should use the version larger than the latest versions in the list below
    private val DecomposeMultilineStringSupportSettings: MigrationItem = MigrationItem(1, _ => {})
    private val AlignTypesInMultilineDeclarations_FromBooleanTo3Values: MigrationItem = MigrationItem(2, _ => {})

    val all: Seq[MigrationItem] = Seq(
      DecomposeMultilineStringSupportSettings,
      AlignTypesInMultilineDeclarations_FromBooleanTo3Values
    )
  }
}