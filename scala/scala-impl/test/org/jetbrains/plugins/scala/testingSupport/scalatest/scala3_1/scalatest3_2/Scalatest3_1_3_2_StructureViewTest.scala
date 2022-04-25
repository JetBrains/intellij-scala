package org.jetbrains.plugins.scala.testingSupport.scalatest.scala3_1.scalatest3_2

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView._
import org.junit.Ignore

class Scalatest3_1_3_2_StructureViewTest extends Scalatest3_1_3_2_Base
  with FeatureSpecFileStructureViewTest
  with FlatSpecFileStructureViewTest
  with FreeSpecFileStructureViewTest
  with FunSuiteFileStructureViewTest
  with PropSpecFileStructureViewTest
  with WordSpecFileStructureViewTest
  with FunSpecFileStructureViewTest {

  //TODO: do not ignore when SCL-20155 is fixed
  @Ignore override def testWordSpecPending(): Unit = ()
  @Ignore override def testWordSpecHierarchy(): Unit = ()
  @Ignore override def testWordSpecNormal(): Unit = ()
}
