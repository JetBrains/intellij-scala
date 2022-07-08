package org.jetbrains.plugins.scala
package testingSupport
package specs2

abstract class SCL7228Test extends Specs2TestCase {

  addSourceFile("SCL7228Test.scala",
    """
      |import org.specs2.mutable.Specification
      |
      |class SCL7228Test extends Specification {
      |  override def is = "foo (bar)" ! (true == true)
      |}
    """.stripMargin
  )

  def testScl7228(): Unit =
    runTestByLocation(loc("SCL7228Test.scala", 3, 1),
      assertConfigAndSettings(_, "SCL7228Test"),
      assertResultTreeHasExactNamedPath(_, TestNodePath("[root]", "SCL7228Test", "foo (bar)"))
    )
}
