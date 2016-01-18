package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest2_2_1

import com.intellij.execution.filters.Filter
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.testingSupport.util.scalatest.ScalaTestFailureLocationFilter

/**
 * @author Roman.Shein
 * @since 31.01.2015.
 */
class GoToFailureLocationTest extends Scalatest2_10_2_2_1_Base {
  def testFailureLocationHyperlink(): Unit = {

    addFileToProject("FailureLocationTest.scala",
          """
            |import org.scalatest._
            |
            |class FailureLocationTest extends FlatSpec with GivenWhenThen {
            |
            | "failed test" should "fail" in {
            |   fail
            | }
            |}
            |
          """.stripMargin
    )

    val project = getProject
    val projectScope = ProjectScope.getProjectScope(project)
    val filter = new ScalaTestFailureLocationFilter(projectScope)
    val errorLocationString = "ScalaTestFailureLocation: FailureLocationTest at (FailureLocationTest.scala:6)"
    var filterRes: Filter.Result = null
    UsefulTestCase.edt(new Runnable() {
      override def run(): Unit = filterRes = filter.applyFilter(errorLocationString, errorLocationString.length)
    })
    assert(filterRes != null)
    assert(filterRes.getFirstHyperlinkInfo != null)
  }
}
