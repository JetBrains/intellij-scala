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

  def testSCL10357(): Unit = doTest {
    s"""
       |trait T1[+X]
       |trait T2[+X] extends T1[X]
       |
        |trait Process[+F[_], +O] {
       |  def ++[F2[x] >: F[x], O2 >: O](p2: Process[F2, O2]): Process[F2, O2]
       |}
       |
        |object Test {
       |  val z: Process[T1, Unit] =
       |    ${caretMarker}(??? : Process[Nothing, Unit]) ++ (??? : Process[T1, Unit])
       |}
       |//true""".stripMargin
  }
}