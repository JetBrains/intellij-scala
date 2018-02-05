package org.jetbrains.plugins.scala.failed.rearranger

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by kate on 5/17/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ScalaFailedRearrangerTest extends RearrangerTest {
  override def shouldPass(): Boolean = false
}
