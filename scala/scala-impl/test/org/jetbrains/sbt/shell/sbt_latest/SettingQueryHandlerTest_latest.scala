package org.jetbrains.sbt.shell.sbt_latest

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.sbt.shell.SettingQueryHandlerTest
import org.junit.experimental.categories.Category

/**
  * Created by Roman.Shein on 27.03.2017.
  */
@Category(Array(classOf[PerfCycleTests]))
class SettingQueryHandlerTest_latest extends SettingQueryHandlerTest {

  override def getPath: String = "sbt/shell/sbtTestRunTest"

}