package org.jetbrains.sbt.shell.sbt_latest

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.sbt.shell.UseSbtTestRunTest
import org.junit.experimental.categories.Category

/**
  * Created by Roman.Shein on 13.04.2017.
  */
@Category(Array(classOf[PerfCycleTests]))
class UseSbtTestRunTest_latest extends UseSbtTestRunTest {
  override def getPath: String = "sbt/shell/sbtTestRunTest"
}
