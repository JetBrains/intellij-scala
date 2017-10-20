package org.jetbrains.sbt.shell.sbt13_7

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.sbt.shell.UseSbtTestRunTest
import org.junit.experimental.categories.Category

/**
  * Created by Roman.Shein on 13.04.2017.
  */
@Category(Array(classOf[SlowTests]))
class UseSbtTestRunTest_13_7 extends UseSbtTestRunTest {
  override def getPath: String = "sbt/shell/sbtTestRunTest_07"
}
