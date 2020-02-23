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
    def this() = this(0) // default constructor is needed by IntelliJ platform
  }

  private[migration]
  case class MigrationItem(version: Int, migrate: ScalaCodeStyleSettings => Unit)

  //noinspection ScalaDeprecation
  private[migration]
  object Migrations {
    val DecomposeMultilineStringSupportSettings: MigrationItem = MigrationItem(1, scalaSettings => {
      import ScalaCodeStyleSettings._
      import scalaSettings._
      MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE = MULTILINE_STRING_SUPORT >= MULTILINE_STRING_QUOTES_AND_INDENT
      MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = MULTILINE_STRING_SUPORT >= MULTILINE_STRING_INSERT_MARGIN_CHAR
    })

    val all: Seq[MigrationItem] = Seq(
      DecomposeMultilineStringSupportSettings
    )
  }
}