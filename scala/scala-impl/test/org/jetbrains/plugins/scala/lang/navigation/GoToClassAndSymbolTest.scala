package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.{FoundItemDescriptor, PSIPresentationBgRendererWrapper, SearchEverywhereContributor, SearchEverywhereUI, SymbolSearchEverywhereContributor}
import com.intellij.ide.util.gotoByName._
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{ActionUiKind, AnActionEvent, Presentation}
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import com.intellij.util.CommonProcessors
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.indexing.FindSymbolParameters
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.navigation.GoToClassAndSymbolTestBase.MyTestSymbolSearchEverywhereContributor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Assert._

import java.util
import java.util.concurrent.{Future, TimeUnit}
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

  /**
   * @param presentableText primary text displayed in the list
   * @param containerText the text of the container displayed as grey hint text
   */
  protected case class GoToElementData(presentableText: String, containerText: String) {
    override def toString: String = this.productIterator.mkString("(", ", ", ")")
  }

  /**
   * This method is more advanced alternative of [[myFixture.getGotoSymbolResults]].
   * This implementation is closer to the actual behavior in the IDE.
   * It has more data for every item shown in the list: psi element, presentable text, container text (shown as a gray hint).
   * It also tests behaviour [[com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider]] implementations
   *
   * Ideally it would be nice to have some API in the platform, see IJPL-174061
   */
  protected def getGotoSymbolE2EResults(text: String): Seq[GoToElementData] = {
    val contributor: SymbolSearchEverywhereContributor = {
      val dataContext = SimpleDataContext.getProjectContext(this.getProject)
      val event = AnActionEvent.createEvent(dataContext, null.asInstanceOf[Presentation], "fake-place-for-tests", ActionUiKind.NONE, null)
      new MyTestSymbolSearchEverywhereContributor(getProject, event, everywhere = false)
    }

    val processor = new CommonProcessors.CollectProcessor[FoundItemDescriptor[AnyRef]]()

    val indicator = new EmptyProgressIndicator
    val presentationWrapper = new PSIPresentationBgRendererWrapper(contributor)
    presentationWrapper.fetchWeightedElements(text, indicator, processor)

    //NOTE: wee need to create `SearchEverywhereUI` on UI thread, otherwise there will be some exceptions
    val elementsFuture: Future[util.List[AnyRef]] =
      invokeAndWait {
        val ui = new SearchEverywhereUI(getProject, List(presentationWrapper).asJava.asInstanceOf[util.List[SearchEverywhereContributor[_]]])
        ui.findElementsForPattern(text)
      }

    assert(!ApplicationManager.getApplication.isDispatchThread, "This method must not be called on EDT as it waits for UI elements to appear")

    val items: Seq[PsiItemWithPresentation] =
      elementsFuture.get(10, TimeUnit.SECONDS).asScala.map(_.asInstanceOf[PsiItemWithPresentation]).toSeq

    items.map { element =>
      GoToElementData(
        element.second.getPresentableText,
        element.second.getContainerText
      )
    }
  }

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

object GoToClassAndSymbolTestBase {
  //NOTE: using a separate class just to be able
  // to easily notice its name in variable watcher during debugging
  private class MyTestSymbolSearchEverywhereContributor(
    project: Project,
    event: AnActionEvent,
    everywhere: Boolean
  ) extends SymbolSearchEverywhereContributor(event: AnActionEvent) {

    this.myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(project, everywhere))
  }
}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class GoToClassAndSymbolCommonTests extends GoToClassAndSymbolTestBase {

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

class GoToClassAndSymbolTest_Scala213 extends GoToClassAndSymbolCommonTests {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class GoToClassAndSymbolTest_Scala3 extends GoToClassAndSymbolCommonTests {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  // We don't run on EDT because we test UI logic, and we use a future to wait for "Search Everywhere" dialog to appear
  override def runInDispatchThread() = false

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testGoToSymbolWithPackagePrefix_ShouldContainAllTopLevelDefinitions(): Unit = {
    val TopLevelDefinitions =
      """class MyClass {
        |  val myValField: Int = 1
        |  var myVarField: Int = 1
        |
        |  def foo(): Unit = {
        |    //These local definitions exist just to ensure they don't appear in the result list
        |    val myValLocal = 1
        |    def myDefLocal = 1
        |    class MyClassLocal
        |  }
        |}
        |object MyObject
        |trait MyTrait
        |enum MyEnum { case MyCase }
        |
        |val myVal1 = 1
        |val (myVal2, myVal3) = (2, 3)
        |
        |var myVar1 = 1
        |var (myVar2, myVar3) = (2, 3)
        |
        |def myFunction: String = "42"
        |
        |extension (s: String)
        |  def myExtension: String = s
        |
        |given myGivenAlias: String = "42"
        |given Short = 42
        |given myGivenDefinition: AnyRef with {}
        |
        |type MyAlias = String"""

    myFixture.addFileToProject("org/example/GoToSymbolWithPackagePrefix.scala",
      s"""package org.example
         |
         |$TopLevelDefinitions
         |""".stripMargin
    )
    myFixture.addFileToProject("some/other/unrelated_package/GoToSymbolWithPackagePrefix.scala",
      s"""package some.other.unrelated_package
         |
         |$TopLevelDefinitions
         |""".stripMargin
    )

    val expectedElements: Seq[GoToElementData] = Seq(
      GoToElementData("MyAlias", "org.example"),
      GoToElementData("MyCase", "org.example.MyEnum"),
      GoToElementData("MyClass", "org.example"),
      GoToElementData("MyEnum", "org.example"),
      GoToElementData("MyObject", "org.example"),
      GoToElementData("MyTrait", "org.example"),
      GoToElementData("myExtension", "org.example"),
      GoToElementData("myFunction", "org.example"),
      GoToElementData("myGivenAlias", "org.example"),
      GoToElementData("myGivenDefinition", "org.example"),
      GoToElementData("myVal1", "org.example"),
      GoToElementData("myVal2", "org.example"),
      GoToElementData("myVal3", "org.example"),
      GoToElementData("myValField", "org.example.MyClass"),
      GoToElementData("myVar1", "org.example"),
      GoToElementData("myVar2", "org.example"),
      GoToElementData("myVar3", "org.example"),
      GoToElementData("myVarField", "org.example.MyClass"),
    )

    val actualElements = getGotoSymbolE2EResults("org.example.my")
    assertCollectionEquals(
      expectedElements.sortBy(_.presentableText),
      actualElements.sortBy(_.presentableText)
    )
  }
}
