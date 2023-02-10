package org.jetbrains.plugins.scala.lang.search

import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.junit.Assert.{assertEquals, assertTrue}

import scala.jdk.CollectionConverters.CollectionHasAsScala

class InheritorsSearchTest extends ScalaLightCodeInsightFixtureTestCase {
  private def doTest(fileText: String, expectedSubclassNames: String*): Unit = {
    val file = configureFromFileText(fileText)
    val caretOffset = getEditor.getCaretModel.getOffset
    assertTrue("Caret position is missing", caretOffset > 0)
    val clazz = file.findElementAt(caretOffset).parentOfType[PsiClass].get
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
    "AA2", "AA3"
  )

  def testTypeAlias_WithAliasedFullyQualifiedName_1(): Unit = doTest(
    s"""package org.example
       |
       |trait ${CARET}Phase
       |object Scope1 {
       |  type Phase = org.example.Phase
       |}
       |
       |class Phase1 extends Phase
       |class Phase2 extends Scope1.Phase
       |
       |package inner_package {
       |  trait Phase //NEW PHASE, UNRELATED TO ORIGINAL
       |  object Scope2 {
       |    type Phase = org.example.inner_package.Phase //REFERENCING NEW PHASE, UNRELATED TO ORIGINAL
       |  }
       |
       |  class Phase3 extends Phase
       |  class Phase4 extends Scope2.Phase
       |}
       |""".stripMargin,
    "Phase1", "Phase2",
  )

  def testTypeAlias_WithAliasedFullyQualifiedName_2(): Unit = doTest(
    s"""package org.example
       |
       |trait ${CARET}Phase
       |object Scope1 {
       |  type Phase = org.example.inner_package.Phase //REFERENCING NEW PHASE, UNRELATED TO ORIGINAL
       |}
       |
       |class Phase1 extends Phase
       |class Phase2 extends Scope1.Phase
       |
       |package inner_package {
       |  trait Phase //NEW PHASE, UNRELATED TO ORIGINAL
       |  object Scope2 {
       |    type Phase = org.example.Phase //REFERENCING ORIGINAL PHASE
       |  }
       |
       |  class Phase3 extends Phase
       |  class Phase4 extends Scope2.Phase
       |}
       |""".stripMargin,
    "Phase1", "Phase4",
  )

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