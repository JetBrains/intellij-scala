package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.{SearchEverywhereManager, SearchEverywhereManagerImpl, SearchEverywhereUI, SymbolSearchEverywhereContributor}
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{ActionUiKind, AnActionEvent, Presentation}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.navigation.GoToSymbolTestBase.GoToElementData
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

import java.util
import java.util.concurrent.{Future, TimeUnit}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.jdk.CollectionConverters._

/**
 * The test covers functionality of "Go to Symbol" action
 * (also available via "Search Everywhere" in the "Symbols" tab)
 *
 * It's different from [[ChooseClassOrSymbolByNameTestBase]]
 * Both of the test classes test implementations of [[com.intellij.navigation.GotoClassContributor]].
 * However, this test is closer to a E2E test rather than unit tests.
 * See [[getGotoSymbolE2EResults]] comment for details
 */
abstract class GoToSymbolTestBase extends ScalaLightCodeInsightFixtureTestCase {

  /**
   * This method is more advanced alternative of [[myFixture.getGotoSymbolResults]].
   * This implementation is closer to the actual behavior in the IDE.
   * It has more data for every item shown in the list: psi element, presentable text, container text (shown as a gray hint).
   * It also tests behaviour [[com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider]] implementations
   *
   * Ideally, it would be nice to have some API in the platform, see IJPL-174061
   */
  protected def getGotoSymbolE2EResults(
    searchText: String,
    waitTimeout: Duration = 10.seconds
  ): Seq[GoToElementData] = {
    assert(!ApplicationManager.getApplication.isDispatchThread, "This method must not be called on EDT as it waits for UI elements to appear")

    //NOTE: we need to create `SearchEverywhereUI` on the UI thread, otherwise there will be some exceptions
    val ui: SearchEverywhereUI = invokeAndWait {
      val searchManager = SearchEverywhereManager.getInstance(getProject).asInstanceOf[SearchEverywhereManagerImpl]
      val tabId = classOf[SymbolSearchEverywhereContributor].getSimpleName
      val event = createDummyActionEvent
      searchManager.show(tabId, "", event)
      searchManager.getCurrentlyShownUI
    }

    // Wait until "SearchEverywhereUI.rebuildList" is invoked (see SearchEverywhereUI.scheduleRebuildList)
    // to avoid exceptions like "SymbolSearchEverywhereContributor has already been disposed"
    Thread.sleep(200)

    val elementsFuture: Future[util.List[AnyRef]] = invokeAndWait {
      ui.findElementsForPattern(searchText)
    }

    val items: Seq[PsiItemWithPresentation] =
      elementsFuture.get(waitTimeout.toSeconds, TimeUnit.SECONDS).asScala.map(_.asInstanceOf[PsiItemWithPresentation]).toSeq

    // Free the popup to avoid memory leaks
    invokeAndWait {
      ui.closePopup()
    }

    items.map { element =>
      GoToElementData(
        element.second.getPresentableText,
        element.second.getContainerText
      )
    }
  }

  private def createDummyActionEvent: AnActionEvent = {
    val context = SimpleDataContext.getProjectContext(this.getProject)
    AnActionEvent.createEvent(context, null.asInstanceOf[Presentation], "fake-place-for-tests", ActionUiKind.NONE, null)
  }
}

object GoToSymbolTestBase {
  /**
   * @param presentableText primary text displayed in the list
   * @param containerText   the text of the container displayed as grey hint text
   */
  case class GoToElementData(presentableText: String, containerText: String) {
    override def toString: String = this.productIterator.mkString("(", ", ", ")")
  }
}

class GoToSymbolTest_Scala3 extends GoToSymbolTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  // We don't run on EDT because we test UI logic,
  // and we use a future to wait for the "Search Everywhere" dialog to appear
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
        |val (myVal2, myVal3, Some(myVal4)) = (2, 3, Option(4))
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
      GoToElementData("myVal4", "org.example"),
      GoToElementData("myValField", "org.example.MyClass"),
      GoToElementData("myVar1", "org.example"),
      GoToElementData("myVar2", "org.example"),
      GoToElementData("myVar3", "org.example"),
      GoToElementData("myVarField", "org.example.MyClass"),
    )

    doToToSymbolTest("org.example.my", expectedElements)
  }

  private def doToToSymbolTest(searchText: String, expectedElements: Seq[GoToElementData]): Unit = {
    val actualElements = getGotoSymbolE2EResults(searchText)
    assertCollectionEquals(
      expectedElements.sortBy(_.presentableText),
      actualElements.sortBy(_.presentableText)
    )
  }

  private val ValVarPatternDefinitionsTopLevel =
    """package org.example
      |
      |val myVal1 = 1
      |val (myVal2, myVal3, Some(myVal4)) = (2, 3, Option(4))
      |
      |var myVar1 = 1
      |var (myVar2, myVar3, Some(myVar4)) = (2, 3, Option(4))
      |""".stripMargin

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testDetectAllIdentifiersFromValVarPatternDefinition_TopLevel_FullyQualifiedName(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala", ValVarPatternDefinitionsTopLevel)

    doToToSymbolTest("org.example.myVal1", Seq(GoToElementData("myVal1", "org.example")))
    doToToSymbolTest("org.example.myVal2", Seq(GoToElementData("myVal2", "org.example")))
    doToToSymbolTest("org.example.myVal3", Seq(GoToElementData("myVal3", "org.example")))
    doToToSymbolTest("org.example.myVal4", Seq(GoToElementData("myVal4", "org.example")))

    doToToSymbolTest("org.example.myVar1", Seq(GoToElementData("myVar1", "org.example")))
    doToToSymbolTest("org.example.myVar2", Seq(GoToElementData("myVar2", "org.example")))
    doToToSymbolTest("org.example.myVar3", Seq(GoToElementData("myVar3", "org.example")))
    doToToSymbolTest("org.example.myVar4", Seq(GoToElementData("myVar4", "org.example")))
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testDetectAllIdentifiersFromValVarPatternDefinition_TopLevel_ShortName(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala", ValVarPatternDefinitionsTopLevel)

    doToToSymbolTest("myVal1", Seq(GoToElementData("myVal1", "org.example")))
    doToToSymbolTest("myVal2", Seq(GoToElementData("myVal2", "org.example")))
    doToToSymbolTest("myVal3", Seq(GoToElementData("myVal3", "org.example")))
    doToToSymbolTest("myVal4", Seq(GoToElementData("myVal4", "org.example")))

    doToToSymbolTest("myVar1", Seq(GoToElementData("myVar1", "org.example")))
    doToToSymbolTest("myVar2", Seq(GoToElementData("myVar2", "org.example")))
    doToToSymbolTest("myVar3", Seq(GoToElementData("myVar3", "org.example")))
    doToToSymbolTest("myVar4", Seq(GoToElementData("myVar4", "org.example")))
  }

  private val ValVarPatternDefinitionsNested =
    """package org.example
      |
      |object MyObject {
      |  object Inner {
      |    val myVal1 = 1
      |    val (myVal2, myVal3, Some(myVal4)) = (2, 3, Option(4))
      |
      |    var myVar1 = 1
      |    var (myVar2, myVar3, Some(myVar4)) = (2, 3, Option(4))
      |  }
      |}
      |""".stripMargin

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testDetectAllIdentifiersFromValVarPatternDefinition_Nested_FullyQualifiedName(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala", ValVarPatternDefinitionsNested)

    doToToSymbolTest("org.example.MyObject.Inner.myVal1", Seq(GoToElementData("myVal1", "org.example.MyObject.Inner")))
    doToToSymbolTest("org.example.MyObject.Inner.myVal2", Seq(GoToElementData("myVal2", "org.example.MyObject.Inner")))
    doToToSymbolTest("org.example.MyObject.Inner.myVal3", Seq(GoToElementData("myVal3", "org.example.MyObject.Inner")))
    doToToSymbolTest("org.example.MyObject.Inner.myVal4", Seq(GoToElementData("myVal4", "org.example.MyObject.Inner")))

    doToToSymbolTest("org.example.MyObject.Inner.myVar1", Seq(GoToElementData("myVar1", "org.example.MyObject.Inner")))
    doToToSymbolTest("org.example.MyObject.Inner.myVar2", Seq(GoToElementData("myVar2", "org.example.MyObject.Inner")))
    doToToSymbolTest("org.example.MyObject.Inner.myVar3", Seq(GoToElementData("myVar3", "org.example.MyObject.Inner")))
    doToToSymbolTest("org.example.MyObject.Inner.myVar4", Seq(GoToElementData("myVar4", "org.example.MyObject.Inner")))
  }

  @WithIndexingMode(mode = IndexingMode.DUMB_FULL_INDEX)
  def testDetectAllIdentifiersFromValVarPatternDefinition_Nested_ShortName(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala", ValVarPatternDefinitionsNested)

    doToToSymbolTest("myVal1", Seq(GoToElementData("myVal1", "org.example.MyObject.Inner")))
    doToToSymbolTest("myVal2", Seq(GoToElementData("myVal2", "org.example.MyObject.Inner")))
    doToToSymbolTest("myVal3", Seq(GoToElementData("myVal3", "org.example.MyObject.Inner")))
    doToToSymbolTest("myVal4", Seq(GoToElementData("myVal4", "org.example.MyObject.Inner")))

    doToToSymbolTest("myVar1", Seq(GoToElementData("myVar1", "org.example.MyObject.Inner")))
    doToToSymbolTest("myVar2", Seq(GoToElementData("myVar2", "org.example.MyObject.Inner")))
    doToToSymbolTest("myVar3", Seq(GoToElementData("myVar3", "org.example.MyObject.Inner")))
    doToToSymbolTest("myVar4", Seq(GoToElementData("myVar4", "org.example.MyObject.Inner")))
  }
}
