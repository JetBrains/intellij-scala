package org.jetbrains.plugins.scala
package testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class SCL7228Test extends Specs2TestCase {

  def testScl7228() { addFileToProject("SCL7228Test.scala",
  """
    |import org.specs2.mutable.Specification
    |
    |class SCL7228Test extends Specification {
    |  override def is = "foo (bar)" ! (true == true)
    |}
  """.stripMargin
  )

    runTestByLocation(3, 1, "SCL7228Test.scala",
      checkConfigAndSettings(_, "SCL7228Test", ""),
      checkResultTreeHasExactNamedPath(_, "[root]", "SCL7228Test", "foo (bar)"),
      debug = true
    )

  }
}
