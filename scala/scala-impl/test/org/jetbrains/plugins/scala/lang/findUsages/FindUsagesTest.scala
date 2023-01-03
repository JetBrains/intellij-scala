package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesHandler, ScalaFindUsagesHandlerFactory, ScalaTypeDefinitionFindUsagesOptions}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.util.Markers
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.collection.immutable.ListSet

abstract class FindUsagesTestBase extends ScalaFixtureTestCase with Markers {

  protected def defaultOptions = new ScalaTypeDefinitionFindUsagesOptions(getProject)

  protected case class MyUsage(
    navigationRange: TextRange,
    navigationText: String
  )

  protected def doTest(
    fileText: String,
  ): Unit = {
    doTest(fileText, defaultOptions)
  }

  protected def doTest(
    fileText0: String,
    options: FindUsagesOptions
  ): Unit = {
    val fileText = fileText0.withNormalizedSeparator
    val (fileTextClean, expectedUsageRanges) = extractNumberedMarkers(fileText)

    val fileTextWithoutCaret = fileTextClean.replace(CARET, "")
    val expectedUsages = expectedUsageRanges.map { range =>
      val textAtRange = range.substring(fileTextWithoutCaret)
      MyUsage(
        navigationRange = range,
        navigationText = textAtRange,
      )
    }

    doTest(fileTextClean, expectedUsages, options)
  }

  protected def doTest(
    fileText: String,
    expectedUsages: Seq[MyUsage],
    options: FindUsagesOptions
  ): Unit = {
    myFixture.configureByText("dummy.scala", fileText.withNormalizedSeparator)

    val elementAtCaret = myFixture.getElementAtCaret
    val namedElement = PsiTreeUtil.getParentOfType(elementAtCaret, classOf[ScNamedElement], false)

    val actualUsages: Seq[MyUsage] = {
      val result = ListSet.newBuilder[MyUsage]

      val usagesProcessor: Processor[UsageInfo] = (usage) => {
        val element = usage.getElement
        val navigationRange = usage.getNavigationRange.asInstanceOf[TextRange]
        val navigationText = element.getContainingFile.getText.substring(navigationRange.getStartOffset, navigationRange.getEndOffset)
        result += MyUsage(navigationRange, navigationText)
        true
      }

      val handler = new ScalaFindUsagesHandler(namedElement, ScalaFindUsagesHandlerFactory.getInstance(getProject))
      handler.processElementUsages(namedElement, usagesProcessor, options)

      result.result().toSeq.sortBy(_.navigationRange.getStartOffset)
    }

    assertCollectionEquals(
      "Usages",
      expectedUsages,
      actualUsages
    )
  }
}

class FindUsagesTest extends FindUsagesTestBase {
  private def classWithMembersOptions: FindUsagesOptions = {
    val options = new ScalaTypeDefinitionFindUsagesOptions(getProject)
    options.isMembersUsages = true
    options
  }

  def testFindObjectWithMembers(): Unit = {
    doTest(
      s"""object ${CARET}Test {
         |  def foo() = ???
         |
         |  ${start(1)}Test${end(1)}.${start(0)}foo${end(0)}
         |}
         |""".stripMargin,
      classWithMembersOptions)
  }

  def testFindValOverriders(): Unit = {
    doTest(
      s"""
         |trait FindMyMembers {
         |  val findMyVal$CARET: Int
         |  def methodInTrait(): Unit = {
         |    println(${start(0)}findMyVal${end(0)})
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  override val findMyVal: Int = 1
         |
         |  def methodInImpl(): Unit = {
         |    println(${start(1)}findMyVal${end(1)})
         |  }
         |}
      """.stripMargin)
  }

  def testFindDefOverriders(): Unit = {
    doTest(
      s"""
         |trait FindMyMembers {
         |  def findMyDef$CARET: Int
         |  def methodInTrait(): Unit = {
         |    println(${start(0)}findMyDef${end(0)})
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  override def findMyDef: Int = 1
         |
         |  def methodInImpl(): Unit = {
         |    println(${start(1)}findMyDef${end(1)})
         |  }
         |}
         |
         |class FindMyMembersImpl2 extends FindMyMembers {
         |  override val findMyDef: Int = 2
         |
         |  def methodInImpl2(): Unit = {
         |    println(${start(2)}findMyDef${end(2)})
         |  }
         |}
         |
         |class FindMyMembersImpl3 extends FindMyMembers {
         |  override var findMyDef: Int = 2
         |
         |  def methodInImpl2(): Unit = {
         |    ${start(3)}findMyDef${end(3)} = 3
         |    println(${start(4)}findMyDef${end(4)})
         |  }
         |}
      """.stripMargin)
  }

  def testUnaryOperator(): Unit = doTest(
    s"""class B {
       |  def ${CARET}unary_! : B = this
       |}
       |
       |object Test {
       |  val b = new B
       |  ${start(0)}!${end(0)}b
       |  b.${start(1)}unary_!${end(1)}
       |  b.${start(2)}unary_$$bang${end(2)}
       |}
       |""".stripMargin)

}


class FindUsagesTest_Scala3 extends FindUsagesTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testTypeParameterInEnumCaseUsedInScalaDoc(): Unit = doTest(
    s"""enum TestEnum [MyTypeParameter](myParameter: Int) {
       |  /**
       |   * @param myParameterInner42 parameter description
       |   * @tparam ${start(0)}MyTypeParameterInner${end(0)} type parameter description
       |   */
       |  case EnumMember[${CARET}MyTypeParameterInner](myParameterInner42: Int)
       |    extends TestEnum[${start(1)}MyTypeParameterInner${end(1)}](myParameterInner42)
       |}
       |""".stripMargin
  )

  def testParameterInEnumCaseUsedInScalaDoc(): Unit = doTest(
    s"""enum TestEnum [MyTypeParameter](myParameter: Int) {
       |  /**
       |   * @param ${start(0)}myParameterInner42${end(0)} parameter description
       |   * @tparam MyTypeParameterInner type parameter description
       |   */
       |  case EnumMember[MyTypeParameterInner](${CARET}myParameterInner42: Int)
       |    extends TestEnum[MyTypeParameterInner](${start(1)}myParameterInner42${end(1)})
       |}
       |""".stripMargin
  )
}