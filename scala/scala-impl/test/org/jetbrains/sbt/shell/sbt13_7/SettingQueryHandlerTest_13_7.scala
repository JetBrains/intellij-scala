package org.jetbrains.sbt.shell.sbt13_7

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.sbt.shell.SettingQueryHandlerTest
import org.junit.experimental.categories.Category

/**
  * Created by Roman.Shein on 27.03.2017.
  */
@Category(Array(classOf[PerfCycleTests]))
class SettingQueryHandlerTest_13_7 extends SettingQueryHandlerTest {

  override def getPath: String = "sbt/shell/sbtTestRunTest_07"

}