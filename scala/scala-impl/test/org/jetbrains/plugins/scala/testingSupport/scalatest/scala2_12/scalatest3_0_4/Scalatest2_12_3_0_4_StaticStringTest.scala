package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_12.scalatest3_0_4

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest._
import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 10.03.2017
 */
@Category(Array(classOf[SlowTests]))
class Scalatest2_12_3_0_4_StaticStringTest extends Scalatest2_12_3_0_4_Base with FeatureSpecStaticStringTest with
  FlatSpecStaticStringTest with FreeSpecStaticStringTest with FunSpecStaticStringTest with FunSuiteStaticStringTest with
  PropSpecStaticStringTest with WordSpecStaticStringTest with MethodsStaticStringTest
