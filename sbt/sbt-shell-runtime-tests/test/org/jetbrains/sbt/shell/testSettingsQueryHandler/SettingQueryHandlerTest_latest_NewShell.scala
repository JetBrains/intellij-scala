package org.jetbrains.sbt.shell.testSettingsQueryHandler

class SettingQueryHandlerTest_latest_NewShell extends SettingQueryHandlerTestBase {
  override def useNewShell: Boolean = true

  override def getRelativeTestProjectPath: String = "sbt-shell-runtime-tests/testdata/sbt/shell/sbtTestRunTest_latest"
}
