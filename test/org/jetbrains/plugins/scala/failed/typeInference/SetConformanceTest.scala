package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 01.04.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class SetConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
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

  def testSCL11060(): Unit = checkTextHasNoErrors(
    s"""
       |def foo:Iterator[(Int, Set[Int])] = {
       |  val tS: (Int, Set[Int]) = (5, Set(12,3))
       |  if (tS._2.nonEmpty) Some(tS).toIterator else None.toIterator
       |}
       |//true
    """.stripMargin)

  def testSCL11139(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.reflect.Manifest
       |object App {
       |  def tryCast[T](o: Any)(implicit manifest: Manifest[T]): Option[T] = {
       |    val clazz = manifest.runtimeClass.asInstanceOf[Class[T]]
       |    if (clazz.isAssignableFrom(o.getClass)) {
       |      Some(o.asInstanceOf[T])
       |    } else {
       |      None
       |    }
       |  }
       |
       |  def main(arg: Array[String]) = {
       |    val text: String = Seq("a", 1)
       |      .flatMap(tryCast[String])
       |      .mkString
       |    println(text)
       |  }
       |}
       |//true
    """.stripMargin)

  def testSCL11140(): Unit = checkTextHasNoErrors(
    s"""
       |import scala.collection.{Map, mutable}
       |import scala.collection.generic.CanBuildFrom
       |
       |object IntelliBugs {
       |  implicit class MapOps[K2, V2, M[K, V] <: Map[K, V]](val m: M[K2, V2]) extends AnyVal {
       |    def mapValuesStrict[V3](f: V2 => V3)(implicit cbf: CanBuildFrom[M[K2, V2], (K2, V3), M[K2, V3]]) =
       |      m.map { case (k, v) => k -> f(v) }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val m = mutable.HashMap.empty[String, Int]
       |    val m2: Map[String, Long] = m.mapValuesStrict(_.toLong)
       |  }
       |}
       |//true
    """.stripMargin)
}
