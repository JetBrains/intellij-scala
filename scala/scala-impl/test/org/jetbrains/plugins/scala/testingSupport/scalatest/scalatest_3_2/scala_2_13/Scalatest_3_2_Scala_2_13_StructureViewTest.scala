package org.jetbrains.plugins.scala.testingSupport.scalatest.scalatest_3_2.scala_2_13

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView._
import org.junit.Ignore

class Scalatest_3_2_Scala_2_13_StructureViewTest extends Scalatest_3_2_Scala_2_13_Base
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
