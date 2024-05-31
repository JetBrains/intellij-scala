package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.ide.util.gotoByName._
import com.intellij.openapi.application.ModalityState
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.testFramework.{NeedsIndex, PlatformTestUtil}
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithAllIndexingModes, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert._
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

abstract class GoToClassAndSymbolTestBase extends GoToTestBase {
  private var myPopup: ChooseByNamePopup = _

  private def createPopup(model: ChooseByNameModel): ChooseByNamePopup = {
    if (myPopup == null) {
      myPopup = ChooseByNamePopup.createPopup(getProject, model, /*context*/ null: PsiElement, "")
    }
    myPopup
  }

  override def tearDown(): Unit = {
    if (myPopup != null) {
      myPopup.close(false)
      myPopup.dispose()
      myPopup = null
    }
    super.tearDown()
  }

  protected def gotoClassElements(text: String): Set[Any] = getPopupElements(new GotoClassModel2(getProject), text)

  protected def gotoSymbolElements(text: String): Set[Any] = getPopupElements(new GotoSymbolModel2(getProject, getTestRootDisposable), text)

  private def getPopupElements(model: ChooseByNameModel, text: String): Set[Any] = {
    calcPopupElements(createPopup(model), text)
  }

  private def calcPopupElements(popup: ChooseByNamePopup, text: String): Set[Any] = {
    val semaphore = new Semaphore(1)
    var result: Set[Any] = null
    popup.scheduleCalcElements(text, false, ModalityState.nonModal(), SelectMostRelevant.INSTANCE, set => {
      result = set.asScala.toSet
      semaphore.up()
    })
    val start = System.currentTimeMillis()
    while (!semaphore.waitFor(10) && System.currentTimeMillis() - start < 10000000) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    result
  }

  protected def checkContainExpected(elements: Set[Any],
                                     expected: (Any => Boolean, String)*): Unit = for {
    (predicate, expectedName) <- expected

    actualNames = elements.filter(predicate).map(actualName)
    if !actualNames.contains(expectedName)
  } fail(s"Element not found: $expectedName, found: $actualNames")

  protected def checkSize(elements: Set[Any], expectedSize: Int): Unit = assertEquals(
    s"Wrong number of elements found, found: $elements",
    expectedSize,
    elements.size
  )
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithAllIndexingModes
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class GoToClassAndSymbolTest extends GoToClassAndSymbolTestBase {
  override protected def loadScalaLibrary = false

  @NeedsIndex.Full
  def testTrait(): Unit = {
    myFixture.addFileToProject("GoToClassSimpleTrait.scala", "trait GoToClassSimpleTrait")

    val elements = gotoClassElements("GoToClassS")

    checkContainExpected(elements, (is[ScTrait], "GoToClassSimpleTrait"))
    checkSize(elements, 1)
  }

  @NeedsIndex.Full
  def testTrait2(): Unit = {
    myFixture.addFileToProject("GoToClassSimpleTrait.scala", "trait GoToClassSimpleTrait")

    val elements = gotoClassElements("GTCS")

    checkContainExpected(elements, (is[ScTrait], "GoToClassSimpleTrait"))
    checkSize(elements, 1)
  }

  @NeedsIndex.Full
  def testObject(): Unit = {
    myFixture.addFileToProject("GoToClassSimpleObject.scala", "object GoToClassSimpleObject")

    val elements = gotoClassElements("GoToClassS")

    checkContainExpected(elements, (is[ScObject], "GoToClassSimpleObject"))
    checkSize(elements, 1)
  }

  @NeedsIndex.Full
  def testPackageObject(): Unit = {
    myFixture.addFileToProject("foo/somePackageName/package.scala",
    """package foo
      |
      |package object somePackageName
    """.stripMargin)

    val elements = gotoClassElements("someP")

    checkContainExpected(elements, (isPackageObject, "foo.somePackageName"))
    checkSize(elements, 1)
  }

  @NeedsIndex.Full
  def testGoToSymbol(): Unit = {
    myFixture.addFileToProject("GoToSymbol.scala",
      """class FooClass {
        |  def fooMethod(): Unit = ()
        |}
        |
        |trait FooTrait {
        |  def fooMethod(): Unit
        |}
      """.stripMargin)

    val elements = gotoSymbolElements("foo")
    checkContainExpected(
      elements,
      (is[ScClass], "FooClass"),
      (is[ScTrait], "FooTrait"),
      (is[ScFunction], "fooMethod"),
      (is[ScFunction], "fooMethod")
    )
  }

  @NeedsIndex.Full
  def testGoToClass(): Unit = {
    myFixture.addFileToProject("GoToClass.scala",
      """class FooClass {
        |  def fooMethod(): Unit = ()
        |}
        |
        |trait FooTrait {
        |  def fooMethod(): Unit
        |}
      """.stripMargin)

    val elements = gotoClassElements("foo")
    checkContainExpected(
      elements,
      (is[ScClass], "FooClass"),
      (is[ScTrait], "FooTrait"),
    )
  }

  @NeedsIndex.ForStandardLibrary
  def testGoToClass_javaStdLib(): Unit = {
    checkContainExpected(
      gotoClassElements("AutoCloseable"),
      (is[PsiClass], "java.lang.AutoCloseable")
    )

    checkContainExpected(
      gotoClassElements("AbstractCollection"),
      (is[PsiClass], "java.util.AbstractCollection")
    )
  }

  @NeedsIndex.Full
  def testClass_:::(): Unit = {
    myFixture.addFileToProject("Colons.scala", "class ::: { def ::: : Unit = () }")

    val elements = gotoClassElements("::")

    checkContainExpected(elements, (is[ScClass], ":::"))
    checkSize(elements, 1)
  }

  @NeedsIndex.Full
  def testSymbol_:::(): Unit = {
    myFixture.addFileToProject("Colons.scala", "class ::: { def ::: : Unit = () }")

    val elements = gotoSymbolElements("::")

    checkContainExpected(elements, (is[ScClass], ":::"), (is[ScFunction], ":::"))
    checkSize(elements, 2)
  }

  @NeedsIndex.Full
  def testSymbolInPackaging_:::(): Unit = {
    myFixture.addFileToProject("threeColons.scala",
      """package test
        |class ::: { def ::: : Unit = () }""".stripMargin
    )

    val elements = gotoSymbolElements("::")

    checkContainExpected(elements, (is[ScClass], "test.:::"), (is[ScFunction], ":::"))
    checkSize(elements, 2)
  }
}
