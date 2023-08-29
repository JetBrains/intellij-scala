package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

abstract class SetConformanceTestBase extends ScalaLightCodeInsightFixtureTestCase

abstract class SetConformanceTestBase_Failing extends ScalaLightCodeInsightFixtureTestCase {

  override protected def shouldPass: Boolean = false

  def testSCL4941(): Unit = checkTextHasNoErrors(
    s"""
       |def f(collect: Iterable[Int]): Unit = {
       |  collect.zipWithIndex.foldLeft(mutable.LinkedHashMap.empty[Int, Set[Int]]) {
       |    case (m, (t1, _)) => m += (t1 -> {
       |      val s = m.getOrElse(t1, mutable.LinkedHashSet.empty)
       |      s
       |    })
       |  }
       |}
       |//true
    """.stripMargin)

  //component(3) = "thing" line makes the test fail with some exception from test framework, it has too many errors
  def testSCL13432(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.reflect.ClassTag
       |import scala.collection.mutable
       |
       |def component[T: ClassTag]: mutable.HashMap[Int, T] = ???
       |
       |component.update(3, "thing")
       |//component(3) = "thing"
       |
       |//true
    """.stripMargin)
}

