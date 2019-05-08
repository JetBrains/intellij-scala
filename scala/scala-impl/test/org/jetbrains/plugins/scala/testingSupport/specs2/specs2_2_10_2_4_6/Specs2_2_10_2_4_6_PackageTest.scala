package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_10_2_4_6

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2PackageTest
import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 06.09.2015.
 */
class Specs2_2_10_2_4_6_PackageTest extends Specs2PackageTest with Specs2_2_10_2_4_6_Base {
  override protected def compilerVmOptions = Some("-Xmx1280M")
}
