package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest3_0_1

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView._
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 10.03.2017
  */
@Category(Array(classOf[SlowTests]))
class Scalatest2_11_3_0_1_StructureViewTest extends Scalatest2_11_3_0_1_Base with FeatureSpecFileStructureViewTest
with FlatSpecFileStructureViewTest with FreeSpecFileStructureViewTest with FunSuiteFileStructureViewTest
with PropSpecFileStructureViewTest with WordSpecFileStructureViewTest with FunSpecFileStructureViewTest
