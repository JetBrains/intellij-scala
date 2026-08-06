package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.ide.util.gotoByName.{ChooseByNameModel, ChooseByNamePopup, GotoClassModel2, GotoSymbolModel2, SelectMostRelevant}
import com.intellij.openapi.application.ModalityState
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Assert._

import scala.jdk.CollectionConverters._

/**
 * The test tests "Search By Name" functionality. Note, it's not available as a direct action by that name.
 * This functionality is available as part of other features, for example in "Run Configurations" when you select
 * a main class for "Application" configuration
 *
 * It's different from [[GoToSymbolTestBase]],
 * though both of the test classes test implementations of [[com.intellij.navigation.GotoClassContributor]]
 *
 * If you want to test "Go To Symbol" functionality available in "Search Everywhere" please consider using [[GoToSymbolTestBase]]
 */
abstract class ChooseClassOrSymbolByNameTestBase extends GoToTestBase {
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

  private def calcPopupElements(popup: ChooseByNamePopup, text: String): Set[Any] = invokeAndWait {
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

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class ChooseClassOrSymbolByNameCommonTests extends ChooseClassOrSymbolByNameTestBase {

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testTrait(): Unit = {
    myFixture.addFileToProject("GoToClassSimpleTrait.scala", "trait GoToClassSimpleTrait")

    val elements = gotoClassElements("GoToClassS")

    checkContainExpected(elements, (is[ScTrait], "GoToClassSimpleTrait"))
    checkSize(elements, 1)
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testTrait2(): Unit = {
    myFixture.addFileToProject("GoToClassSimpleTrait.scala", "trait GoToClassSimpleTrait")

    val elements = gotoClassElements("GTCS")

    checkContainExpected(elements, (is[ScTrait], "GoToClassSimpleTrait"))
    checkSize(elements, 1)
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testObject(): Unit = {
    myFixture.addFileToProject("GoToClassSimpleObject.scala", "object GoToClassSimpleObject")

    val elements = gotoClassElements("GoToClassS")

    checkContainExpected(elements, (is[ScObject], "GoToClassSimpleObject"))
    checkSize(elements, 1)
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
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

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
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

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testGoToSymbolWithPackagePrefix_ShouldNotContainLocalDefinitions(): Unit = {
    myFixture.addFileToProject("GoToSymbolWithPackagePrefix.scala",
      """package org.example
        |
        |def myTopLevelDef(): Unit = {
        |  class MyClass
        |  object MyObject
        |  trait MyTrait
        |  enum MyEnum { case MyCase }
        |
        |  val myVal1 = 1
        |  val (myVal2, myVal3) = (2, 3)
        |
        |  var myVar1 = 1
        |  var (myVar2, myVar3) = (2, 3)
        |
        |  def myFunction: String = "42"
        |
        |  extension (s: String)
        |    def myExtension: String = s
        |
        |  given myGivenAlias: String = "42"
        |  given Short = 42
        |  given myGivenDefinition: AnyRef with {}
        |
        |  type MyAlias = String
        |}
        |""".stripMargin
    )

    val expectedNames = Seq(
      "org.example.myTopLevelDef",
    )

    val elements = gotoSymbolElements("org.example.my")
    val actualNames = elements.map(actualName).toSeq

    assertCollectionEquals(
      expectedNames.sorted,
      actualNames.sorted
    )
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
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

  @WithIndexingMode(mode = IndexingMode.DUMB_RUNTIME_ONLY_INDEX)
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

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testClass_:::(): Unit = {
    myFixture.addFileToProject("Colons.scala", "class ::: { def ::: : Unit = () }")

    val elements = gotoClassElements("::")

    checkContainExpected(elements, (is[ScClass], ":::"))
    checkSize(elements, 1)
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testSymbol_:::(): Unit = {
    myFixture.addFileToProject("Colons.scala", "class ::: { def ::: : Unit = () }")

    val elements = gotoSymbolElements("::")

    checkContainExpected(elements, (is[ScClass], ":::"), (is[ScFunction], ":::"))
    checkSize(elements, 2)
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
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

class ChooseClassOrSymbolByNameTest_Scala213 extends ChooseClassOrSymbolByNameCommonTests {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ChooseClassOrSymbolByNameTest_Scala3 extends ChooseClassOrSymbolByNameCommonTests {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3
}
