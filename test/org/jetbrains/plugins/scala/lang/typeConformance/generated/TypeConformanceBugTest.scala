package org.jetbrains.plugins.scala.lang.typeConformance
package generated

class TypeConformanceBugTest extends TypeConformanceTestBase {
  override def folderPath: String = super.folderPath + "bug/"

  def testSCL2244() {doTest()}

  def testSCL2549A() {doTest()}

  def testSCL2549B() {doTest()}

  def testSCL2978() {doTest()}

  def testSCL3363() {doTest()}

  def testSCL3364() {doTest()}

  def testSCL3825() {doTest()}

  def testSCL4278() {doTest()}

  def testSCL9627() {doTest()}

  def testSCL9877_2() {doTest()}

  def testSCL9877_3() {doTest()}

  def testSCL10237() {doTest()}

  def testSCL10237_1() {doTest()}

  def testSCL10237_2() {doTest()}

  def testSCL10237_3() {doTest()}

  def testSCL10432_1() {doTest()}

  def testSCL10432_2() {doTest()}

  def testSCL10357() {doTest()}

  def testSCL8980_1() {doTest()}

  def testSCL8980_2() {doTest()}

  def testSCL11060() {doTest()}

  def testSCL12202(): Unit = doTest()

  def testSCL11320() {doTest(checkEquivalence = true)}

  def test3074(): Unit = doTest(
    """
      |val a: Array[Byte] = Array(1, 2, 3)
      |
      |/* True */
    """.stripMargin)

  def testSCL11140(): Unit = doTest(
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
       |
       |    $caretMarker
       |    val m2: Map[String, Long] = m.mapValuesStrict(_.toLong)
       |  }
       |}
       |//true
    """.stripMargin)

  def testSCL11060_2(): Unit = doTest(
    s"""
       |val foo: Iterator[(Int, Set[Int])] = {
       |  val tS: (Int, Set[Int]) = (5, Set(12,3))
       |  if (tS._2.nonEmpty) Some(tS).toIterator else None.toIterator
       |}
       |//true
    """.stripMargin)
}