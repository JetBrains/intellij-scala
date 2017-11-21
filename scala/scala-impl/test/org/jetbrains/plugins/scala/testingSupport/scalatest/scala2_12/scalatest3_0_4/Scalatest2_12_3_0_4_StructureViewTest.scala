package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_12.scalatest3_0_4

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView._
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 10.03.2017
  */
@Category(Array(classOf[SlowTests]))
class Scalatest2_12_3_0_4_StructureViewTest extends Scalatest2_12_3_0_4_Base with FeatureSpecFileStructureViewTest
with FlatSpecFileStructureViewTest with FreeSpecFileStructureViewTest with FunSuiteFileStructureViewTest
with PropSpecFileStructureViewTest with WordSpecFileStructureViewTest with FunSpecFileStructureViewTest
