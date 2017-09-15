package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest2_2_1

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest._
import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 24.06.2015.
 */
@Category(Array(classOf[SlowTests]))
class Scalatest2_11_2_2_1_StaticStringTest extends Scalatest2_11_2_2_1_Base with FeatureSpecStaticStringTest with
  FlatSpecStaticStringTest with FreeSpecStaticStringTest with FunSpecStaticStringTest with FunSuiteStaticStringTest with
  PropSpecStaticStringTest with WordSpecStaticStringTest with MethodsStaticStringTest
