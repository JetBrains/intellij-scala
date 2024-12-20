package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.{AbstractGotoSEContributor, FoundItemDescriptor, PSIPresentationBgRendererWrapper, SearchEverywhereContributor, SearchEverywhereUI, SymbolSearchEverywhereContributor}
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{ActionUiKind, AnActionEvent, Presentation}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import com.intellij.util.CommonProcessors
import com.intellij.util.indexing.FindSymbolParameters
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.navigation.GoToSymbolTestBase.{GoToElementData, MyTestSymbolSearchEverywhereContributor}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

import java.util
import java.util.concurrent.{Future, TimeUnit}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.jdk.CollectionConverters._

/**
 * The test covers functionality of "Go to Symbol" action
 * (also available voa "Search Everywhere" in "Symbols" tab)
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
   * Ideally it would be nice to have some API in the platform, see IJPL-174061
   */
  protected def getGotoSymbolE2EResults(
    text: String,
    waitTimeout: Duration = 10.seconds
  ): Seq[GoToElementData] = {
    val contributor: AbstractGotoSEContributor = {
      val dataContext = SimpleDataContext.getProjectContext(this.getProject)
      val event = AnActionEvent.createEvent(dataContext, null.asInstanceOf[Presentation], "fake-place-for-tests", ActionUiKind.NONE, null)
      new MyTestSymbolSearchEverywhereContributor(getProject, event, everywhere = false)
    }

    //NOTE: wee need to create `SearchEverywhereUI` on UI thread, otherwise there will be some exceptions
    val elementsFuture: Future[util.List[AnyRef]] =
      invokeAndWait {
        val presentationWrapper = new PSIPresentationBgRendererWrapper(contributor)
        val ui = new SearchEverywhereUI(getProject, List(presentationWrapper).asJava.asInstanceOf[util.List[SearchEverywhereContributor[_]]])
        ui.findElementsForPattern(text)
      }

    assert(!ApplicationManager.getApplication.isDispatchThread, "This method must not be called on EDT as it waits for UI elements to appear")

    val items: Seq[PsiItemWithPresentation] =
      elementsFuture.get(waitTimeout.toSeconds, TimeUnit.SECONDS).asScala.map(_.asInstanceOf[PsiItemWithPresentation]).toSeq

    items.map { element =>
      GoToElementData(
        element.second.getPresentableText,
        element.second.getContainerText
      )
    }
  }
}

object GoToSymbolTestBase {
  //NOTE: using a separate class just to be able
  // to easily notice its name in variable watcher during debugging
  private class MyTestSymbolSearchEverywhereContributor(
    project: Project,
    event: AnActionEvent,
    everywhere: Boolean
  ) extends SymbolSearchEverywhereContributor(event: AnActionEvent) {

    this.myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(project, everywhere))
  }

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
