package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

class ScalaReferencesCodeVisionProviderTest extends ScalaCodeVisionTestBase {

  override protected def enabledGroupIds: Set[String] = Set("references")

  def testUsages(): Unit = doTest(
    """class A {/*<# [2 usages] #>*/
      |  def foo(): Int = 1/*<# [2 usages] #>*/
      |  def unused(): Int = 2
      |}
      |
      |object Main {
      |  val a = new A/*<# [1 usage] #>*/
      |  a.foo()
      |  new A().foo()
      |}
      |""".stripMargin
  )
}
