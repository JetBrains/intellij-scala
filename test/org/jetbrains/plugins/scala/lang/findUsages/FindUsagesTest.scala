package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.collection.JavaConverters._

/**
  * Nikolay.Tropin
  * 21-Aug-17
  */
class FindUsagesTest extends ScalaFixtureTestCase {
  protected def doTest(fileText: String, usageCount: Int): Unit = {
    myFixture.configureByText("dummy.scala", StringUtil.convertLineSeparators(fileText))
    val elem = myFixture.getElementAtCaret
    val named = PsiTreeUtil.getParentOfType(elem, classOf[ScNamedElement], false)
    val coll = myFixture.findUsages(named)

    def message = {
      val usagesString =
        coll.asScala.toSeq.sortBy(_.getSegment.getStartOffset)
        .map(u => s"${u.getElement}, range: ${u.getSegment}").mkString("\n")
      s"Expected $usageCount usages, found:\n$usagesString"
    }

    assert(coll.size() == usageCount, message)
  }

  def testFindValOverriders(): Unit = {
    doTest(
      """
        |trait FindMyMembers {
        |  val findMyVal<caret>: Int
        |  def methodInTrait(): Unit = {
        |    println(findMyVal)
        |  }
        |}
        |
        |class FindMyMembersImpl extends FindMyMembers {
        |  override val findMyVal: Int = 1
        |
        |  def methodInImpl(): Unit = {
        |    println(findMyVal)
        |  }
        |}
      """.stripMargin, 2)
  }

  def testFindDefOverriders(): Unit = {
    doTest(
      """
        |trait FindMyMembers {
        |  def findMyDef<caret>: Int
        |  def methodInTrait(): Unit = {
        |    println(findMyDef)
        |  }
        |}
        |
        |class FindMyMembersImpl extends FindMyMembers {
        |  override def findMyDef: Int = 1
        |
        |  def methodInImpl(): Unit = {
        |    println(findMyDef)
        |  }
        |}
        |
        |class FindMyMembersImpl2 extends FindMyMembers {
        |  override val findMyDef: Int = 2
        |
        |  def methodInImpl2(): Unit = {
        |    println(findMyDef)
        |  }
        |}
        |
        |class FindMyMembersImpl3 extends FindMyMembers {
        |  override var findMyDef: Int = 2
        |
        |  def methodInImpl2(): Unit = {
        |    findMyDef = 3
        |    println(findMyDef)
        |  }
        |}
      """.stripMargin, 5)
  }

}
