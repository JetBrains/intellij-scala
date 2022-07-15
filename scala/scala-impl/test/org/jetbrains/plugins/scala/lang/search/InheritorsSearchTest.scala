package org.jetbrains.plugins.scala
package lang.search

import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.CollectionHasAsScala

@Category(Array(classOf[LanguageTests]))
class InheritorsSearchTest extends ScalaLightCodeInsightFixtureTestAdapter {
  private def doTest(fileText: String, expectedSubclassNames: String*): Unit = {
    val file = configureFromFileText(fileText)
    assertTrue("Caret position is missing", getEditorOffset > 0)
    val clazz = file.findElementAt(getEditorOffset).parentOfType[PsiClass].get
    val inheritors =
      ClassInheritorsSearch.search(clazz, clazz.getUseScope, true)
        .findAll
        .asScala
        .map(_.name)

    assertEquals(expectedSubclassNames.toSet, inheritors.toSet)
  }

  def testSimple(): Unit = doTest(
    s"""
       |trait ${CARET}A1
       |class A2 extends A1
       |trait A3 extends A1
       |
       |class A4 extends A2
       |""".stripMargin,
    "A2", "A3", "A4"
  )

  def testSimpleLocal(): Unit = doTest(
    s"""
       |object X {
       |  def foo(): Int = {
       |    trait ${CARET}A1
       |    class A2 extends A1
       |    trait A3 extends A1
       |
       |    class A4 extends A2
       |  }
       |}
       |""".stripMargin,
    "A2", "A3", "A4"
  )

  def testTypeAlias(): Unit = doTest(
    s"""
       |trait ${CARET}A1
       |object X {
       |  type AA1 = A1
       |
       |  class AA2 extends AA1
       |}
       |
       |class AA3 extends X.AA1
       |""".stripMargin,
    "AA2", "AA3")

  def testTypeAliasLocal(): Unit = doTest(
    s"""
       |object X {
       |  def foo(): Int = {
       |    trait ${CARET}A1
       |    object X {
       |      type AA1 = A1
       |      class AA2 extends AA1
       |    }
       |    class AA3 extends X.AA1
       |
       |    42
       |  }
       |}
       |""".stripMargin,
    "AA2", "AA3")


  def testImportAlias(): Unit = doTest(
    s"""
       |object X {
       |  trait ${CARET}A
       |}
       |
       |object Y {
       |  import X.{A => B}
       |
       |  class B1 extends B
       |  class B2 extends X.A
       |}
       |""".stripMargin,
    "B1", "B2")

  def testImportAliasLocal(): Unit = doTest(
    s"""
       |object Y {
       |  def foo(): Int = {
       |    object X {
       |      trait ${CARET}A
       |    }
       |
       |    object Y {
       |      import X.{A => B}
       |
       |      class B1 extends B
       |      class B2 extends X.A
       |    }
       |
       |    42
       |  }
       |}

       |""".stripMargin,
    "B1", "B2")


  //SCL-18672
  def testPrivateSealedTrait(): Unit = doTest(
    s"""
       |object Example {
       |  private sealed trait ${CARET}T
       |  private object T {
       |    trait A1 extends T
       |    class A2() extends T
       |    case class A3() extends T
       |    object A4 extends T
       |    sealed trait A5 extends T
       |    sealed class A6() extends T
       |    sealed case class A7() extends T
       |  }
       |}""".stripMargin,
    "A1", "A2", "A3", "A4", "A5", "A6", "A7"
  )

  def testSealedTraitLocal(): Unit = doTest(
    s"""
       |class HierarchyTest {
       |  def foo(): Unit = {
       |    sealed trait ${CARET}ReturnResult
       |    case class SuccessResult(testName: String, middleName: String) extends ReturnResult
       |    case object NotFoundResult extends ReturnResult
       |    case object WrongResult extends ReturnResult
       |  }
       |}
       |""".stripMargin,
  "SuccessResult", "NotFoundResult", "WrongResult")
}