package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest2_2_1

import com.intellij.execution.filters.Filter
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.testingSupport.util.scalatest.ScalaTestFailureLocationFilter
import org.junit.Assert._

class GoToFailureLocationTest extends Scalatest2_10_2_2_1_Base {

  addSourceFile("FailureLocationTest.scala",
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

  def testFailureLocationHyperlink(): Unit = {
    val project = getProject
    val projectScope = ProjectScope.getProjectScope(project)
    val filter = new ScalaTestFailureLocationFilter(projectScope)
    val errorLocationString = "ScalaTestFailureLocation: FailureLocationTest at (FailureLocationTest.scala:6)"
    var filterRes: Filter.Result = null
    EdtTestUtil.runInEdtAndWait(() =>
      filterRes = filter.applyFilter(errorLocationString, errorLocationString.length)
    )
    assertNotNull(filterRes)
    assertNotNull(filterRes.getFirstHyperlinkInfo)
  }
}
