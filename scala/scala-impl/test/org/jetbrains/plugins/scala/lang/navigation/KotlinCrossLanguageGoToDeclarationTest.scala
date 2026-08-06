package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findAllTargetElements
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.OptionOpsForTest._
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, LocalJarLibraryLoader}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue, fail}
import org.junit.experimental.categories.Category

import java.nio.file.Path

@Category(Array(classOf[TypecheckerTests]))
abstract class KotlinCrossLanguageGoToDeclarationTestBase_KTIJ_38455 extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_12

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.9.22"))

  protected def prepareStartupActivityFixture(): Unit

  def testScalaCode_DirectBaseClass_CallSiteNavigation(): Unit =
    doCallSiteTest(
      fileName = "Caller.scala",
      fileText =
        s"""import org.example.StartupActivity
           |
           |final class ScalaCaller {
           |  def test(other: StartupActivity, project: String): Unit = {
           |    other.run${CARET}Activity(project)
           |  }
           |}
           |""".stripMargin
    )

  def testScalaCode_NestedBaseClass_CallSiteNavigation(): Unit =
    doCallSiteTest(
      fileName = "Caller.scala",
      fileText =
        s"""import org.example.StartupActivity
           |
           |final class ScalaCaller {
           |  def test(other: StartupActivity.DumbAwareInner, project: String): Unit = {
           |    other.run${CARET}Activity(project)
           |  }
           |}
           |""".stripMargin
    )

  def testScalaCode_OuterBaseClass_CallSiteNavigation(): Unit =
    doCallSiteTest(
      fileName = "Caller.scala",
      fileText =
        s"""import org.example.DumbAwareOuter
           |
           |final class ScalaCaller {
           |  def test(other: DumbAwareOuter, project: String): Unit = {
           |    other.run${CARET}Activity(project)
           |  }
           |}
           |""".stripMargin
    )

  def testJavaCode_DirectBaseClass_CallSiteNavigation(): Unit =
    doCallSiteTest(
      fileName = "Caller.java",
      fileText =
        s"""import org.example.StartupActivity;
           |
           |final class JavaCaller {
           |  void test(StartupActivity other, String project) {
           |    other.run${CARET}Activity(project);
           |  }
           |}
           |""".stripMargin
    )

  def testJavaCode_NestedBaseClass_CallSiteNavigation(): Unit =
    doCallSiteTest(
      fileName = "Caller.java",
      fileText =
        s"""import org.example.StartupActivity;
           |
           |final class JavaCaller {
           |  void test(StartupActivity.DumbAwareInner other, String project) {
           |    other.run${CARET}Activity(project);
           |  }
           |}
           |""".stripMargin
    )

  def testKotlinCode_DirectBaseClass_CallSiteNavigation(): Unit =
    doCallSiteTest(
      fileName = "Caller.kt",
      fileText =
        s"""import org.example.StartupActivity
           |
           |class KotlinCaller {
           |    fun test(other: StartupActivity, project: String) {
           |        other.run${CARET}Activity(project)
           |    }
           |}
           |""".stripMargin
    )

  def testKotlinCode_NestedBaseClass_CallSiteNavigation(): Unit =
    doCallSiteTest(
      fileName = "Caller.kt",
      fileText =
        s"""import org.example.StartupActivity
           |
           |class KotlinCaller {
           |    fun test(other: StartupActivity.DumbAwareInner, project: String) {
           |        other.run${CARET}Activity(project)
           |    }
           |}
           |""".stripMargin
    )

  def testScalaCode_DirectBaseClass_OverrideGutterNavigation(): Unit =
    doOverrideGutterNavigationTest(
      fileName = "Caller.scala",
      fileText =
        s"""import org.example.StartupActivity
           |
           |final class ScalaCaller extends StartupActivity {
           |  override def run${CARET}Activity(project: String): Unit = ()
           |}
           |""".stripMargin
    )

  def testScalaCode_NestedBaseClass_OverrideGutterNavigation(): Unit =
    doOverrideGutterNavigationTest(
      fileName = "Caller.scala",
      fileText =
        s"""import org.example.StartupActivity
           |
           |final class ScalaCaller extends StartupActivity.DumbAwareInner {
           |  override def run${CARET}Activity(project: String): Unit = ()
           |}
           |""".stripMargin
    )

  def testScalaCode_OuterBaseClass_OverrideGutterNavigation(): Unit =
    doOverrideGutterNavigationTest(
      fileName = "Caller.scala",
      fileText =
        s"""import org.example.DumbAwareOuter
           |
           |final class ScalaCaller extends DumbAwareOuter {
           |  override def run${CARET}Activity(project: String): Unit = ()
           |}
           |""".stripMargin
    )

  def testJavaCode_DirectBaseClass_OverrideGutterNavigation(): Unit =
    doOverrideGutterNavigationTest(
      fileName = "Caller.java",
      fileText =
        s"""import org.example.StartupActivity;
           |
           |final class JavaCaller implements StartupActivity {
           |  @Override
           |  public void run${CARET}Activity(String project) {}
           |}
           |""".stripMargin
    )

  def testJavaCode_NestedBaseClass_OverrideGutterNavigation(): Unit =
    doOverrideGutterNavigationTest(
      fileName = "Caller.java",
      fileText =
        s"""import org.example.StartupActivity;
           |
           |final class JavaCaller implements StartupActivity.DumbAwareInner {
           |  @Override
           |  public void run${CARET}Activity(String project) {}
           |}
           |""".stripMargin
    )

  def testKotlinCode_DirectBaseClass_OverrideGutterNavigation(): Unit =
    doOverrideGutterNavigationTest(
      fileName = "Caller.kt",
      fileText =
        s"""import org.example.StartupActivity
           |
           |class KotlinCaller : StartupActivity {
           |    override fun run${CARET}Activity(project: String) {}
           |}
           |""".stripMargin
    )

  def testKotlinCode_NestedBaseClass_OverrideGutterNavigation(): Unit =
    doOverrideGutterNavigationTest(
      fileName = "Caller.kt",
      fileText =
        s"""import org.example.StartupActivity
           |
           |class KotlinCaller : StartupActivity.DumbAwareInner {
           |    override fun run${CARET}Activity(project: String) {}
           |}
           |""".stripMargin
    )

  private def doCallSiteTest(fileName: String, fileText: String): Unit =
    doNavigationTest(fileName, fileText)

  private def doOverrideGutterNavigationTest(fileName: String, fileText: String): Unit =
    doSuperMemberGutterNavigationTest(fileName, fileText)

  private def doNavigationTest(fileName: String, fileText: String): Unit = {
    prepareStartupActivityFixture()
    configureFromFileText(fileName, fileText)
    // Ensure that the test data and the project setup are correct and there are no errors in the test file
    myFixture.checkHighlighting()

    val editor = getEditor
    val targets = findAllTargetElements(getProject, editor, editor.getCaretModel.getOffset).toSet
    val navigableTargets = targets.map(_.getNavigationElement).toSeq
    val targetsDescription = targets.map(NavigationElementUtils.describeTarget).mkString(", ")
    val navigableTargetsDescription = navigableTargets.map(NavigationElementUtils.describeElement).mkString(", ")

    if (navigableTargets.isEmpty) {
      fail(
        s"""Expected exactly one navigable target, got none for file $fileName.
           |Raw targets: [$targetsDescription]""".stripMargin
      )
    }
    if (navigableTargets.size > 1) {
      fail(
        s"""Expected exactly one navigable target, got ${navigableTargets.size} for file $fileName.
           |Navigable targets: [$navigableTargetsDescription]. Raw targets: [$targetsDescription]""".stripMargin
      )
    }

    val navigableTarget = navigableTargets.head

    assertTrue(
      s"Expected runActivity navigable target by name, got: ${NavigationElementUtils.describeElement(navigableTarget)}",
      isNamedElementWithName(navigableTarget, "runActivity")
    )

    assertTrue(
      s"Expected StartupActivity.kt navigable target, got: ${NavigationElementUtils.describeElement(navigableTarget)}",
      isFromStartupActivityKotlinSourceFile(navigableTarget)
    )
  }

  private def doSuperMemberGutterNavigationTest(fileName: String, fileText: String): Unit = {
    prepareStartupActivityFixture()
    configureFromFileText(fileName, fileText)
    // Ensure that the test data and the project setup are correct and there are no errors in the test file
    myFixture.checkHighlighting()

    val gutterNavigationFixture = new SuperMemberGutterNavigationFixture(myFixture)
    val targetAtCaret = gutterNavigationFixture.navigateToSuperMemberTarget(fileName)

    assertTrue(
      s"Expected runActivity target by name after gutter navigation, got: ${NavigationElementUtils.describeElement(targetAtCaret)}",
      isNamedElementWithName(targetAtCaret, "runActivity")
    )

    assertTrue(
      s"Expected StartupActivity.kt target after gutter navigation, got: ${NavigationElementUtils.describeElement(targetAtCaret)}",
      isFromStartupActivityKotlinSourceFile(targetAtCaret)
    )
  }

  private def isNamedElementWithName(element: PsiElement, expectedName: String): Boolean = element match {
    case named: PsiNamedElement =>
      named.getName == expectedName
    case _ =>
      false
  }

  private def isFromStartupActivityKotlinSourceFile(element: PsiElement): Boolean = {
    val filePath = NavigationElementUtils.elementLocationPath(element)
    filePath.endsWith("StartupActivity.kt")
  }
}

object KotlinCrossLanguageGoToDeclarationTestBase_KTIJ_38455 {
  //language=kotlin
  val StartupActivityKotlinSource: String =
    """package org.example
      |
      |interface StartupActivity {
      |    fun runActivity(project: String)
      |
      |    interface DumbAwareInner : StartupActivity
      |}
      |
      |interface DumbAwareOuter : StartupActivity
      |""".stripMargin
}

/**
 * Baseline scenario: a common Kotlin class is in project sources.
 */
class KotlinCrossLanguageGoToDeclarationTest_KTIJ_38455 extends KotlinCrossLanguageGoToDeclarationTestBase_KTIJ_38455 {

  override protected def prepareStartupActivityFixture(): Unit = {
    myFixture.addFileToProject(
      "org/example/StartupActivity.kt",
      KotlinCrossLanguageGoToDeclarationTestBase_KTIJ_38455.StartupActivityKotlinSource
    )
  }
}

/**
 * Main scenario for KTIJ-38455: common Kotlin class comes from a compiled library with attached sources.
 */
class KotlinCrossLanguageGoToDeclaration_WithCompiledLibraryDependencyTest_KTIJ_38455 extends KotlinCrossLanguageGoToDeclarationTestBase_KTIJ_38455 {

  private val librariesRoot = Path.of(TestUtils.getTestDataPath, "lang", "navigation", "kotlinCompiledLibrary-KTIJ-38455")

  private val kotlinCompiledLibraryLoader = LocalJarLibraryLoader(
    libraryName = "kotlin-nav-lib",
    classesJarPath = librariesRoot.resolve("kotlin-nav-lib.jar"),
    sourcesJarPath = Some(librariesRoot.resolve("kotlin-nav-lib-sources.jar"))
  )

  override protected def additionalLibraries: Seq[LibraryLoader] =
    super.additionalLibraries :+ kotlinCompiledLibraryLoader

  // If we reuse the project, the libraries will be disposed of after the first test run and other tests will fail.
  // NOTE: there seems to be some issue with the test libraries setup/disposal.
  // I would expect the libraries to remain between test runts IF we explicitly tell reusing the project.
  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare

  override protected def prepareStartupActivityFixture(): Unit = ()

  override def setUpLibraries(module: Module): Unit = {
    super.setUpLibraries(module)

    val testLibrary = kotlinCompiledLibraryLoader.library.getOrFail("LocalJarLibraryLoader did not register kotlin-nav-lib library")
    assertKotlinNavigationLibraryIsCorrect(testLibrary)
  }

  private def assertKotlinNavigationLibraryIsCorrect(testLibrary: Library): Unit = {
    val sourceUrls = testLibrary.getUrls(OrderRootType.SOURCES).toSeq
    assertTrue(
      s"Expected sources jar root to be attached, got: ${sourceUrls.mkString(", ")}",
      sourceUrls.exists(_.contains("kotlin-nav-lib-sources.jar"))
    )

    val sourcesRoot = kotlinCompiledLibraryLoader.sourcesRoot.getOrFail("LocalJarLibraryLoader did not register sources root for kotlin-nav-lib")
    assertLibrarySourceContentMatchesSourceBasedContent(sourcesRoot)
  }

  private def assertLibrarySourceContentMatchesSourceBasedContent(sourcesRoot: VirtualFile): Unit = {
    val startupActivityInSourcesJar = sourcesRoot.findFileByRelativePath("org/example/StartupActivity.kt")
    assertNotNull("Could not find org/example/StartupActivity.kt in kotlin-nav-lib-sources.jar", startupActivityInSourcesJar)

    val sourceFromJar = VfsUtilCore.loadText(startupActivityInSourcesJar)
    assertEquals(
      "Source text in kotlin-nav-lib-sources.jar differs from KotlinCrossLanguageGoToDeclarationTestBase_KTIJ_38455 fixture",
      KotlinCrossLanguageGoToDeclarationTestBase_KTIJ_38455.StartupActivityKotlinSource.trim,
      sourceFromJar.trim
    )
  }

  // TODO: patch when KTIJ-38455 is fixed
  override def testScalaCode_NestedBaseClass_CallSiteNavigation(): Unit = runWithExpectedOutcomeUntilKTIJ38455Fixed {
    super.testScalaCode_NestedBaseClass_CallSiteNavigation()
  }

  // TODO: patch when KTIJ-38455 is fixed
  override def testScalaCode_NestedBaseClass_OverrideGutterNavigation(): Unit = runWithExpectedOutcomeUntilKTIJ38455Fixed {
    super.testScalaCode_NestedBaseClass_OverrideGutterNavigation()
  }

  private def runWithExpectedOutcomeUntilKTIJ38455Fixed(testBody: => Unit): Unit = {
    var bodySucceeded = false
    try {
      testBody
      bodySucceeded = true
    } catch {
      case _: Throwable =>
        ()
    }

    if (bodySucceeded) {
      fail(
        "TODO[KTIJ-38455]: remove this expectation reversal once KTIJ-38455 is fixed in Kotlin plugin. " +
          "This test must fail while the upstream bug is unresolved."
      )
    }
  }
}
