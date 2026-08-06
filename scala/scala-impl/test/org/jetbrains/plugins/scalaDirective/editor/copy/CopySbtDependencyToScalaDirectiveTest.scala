package org.jetbrains.plugins.scalaDirective.editor.copy

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.extensions.{StringExt, invokeAndWait}
import org.jetbrains.plugins.scala.lang.actions.editor.copy.CopyPasteTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.DefaultInvocationCount
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchClientTesting
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil

final class CopySbtDependencyToScalaDirectiveTest extends CopyPasteTestBase with PackageSearchClientTesting {
  //region Completion auto popups after paste test configuration
  private[this] var completionAutoPopupTester: CompletionAutoPopupTester = _
  private[this] var scalaCompletionTestFixture: ScalaCompletionTestFixture = _

  override def setUp(): Unit = {
    super.setUp()
    completionAutoPopupTester = new CompletionAutoPopupTester(myFixture)
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
    // pick up updated scala and java fixtures after super.setUp()
    scalaCompletionTestFixture = new ScalaCompletionTestFixture(
      scalaFixture,
      DefaultInvocationCount
    )
  }

  override protected def runInDispatchThread(): Boolean = false

  override protected def runTestRunnable(testRunnable: ThrowableRunnable[Throwable]): Unit =
    completionAutoPopupTester.runWithAutoPopupEnabled(testRunnable)
  //endregion

  //region Tests
  //region Different keys
  def testSimpleSbtDependency_depKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using dep $CARET
       |""".stripMargin,
    s"""
       |//> using dep org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_depsKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_dependenciesKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using dependencies $CARET
       |""".stripMargin,
    s"""
       |//> using dependencies org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_testDepKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using test.dep $CARET
       |""".stripMargin,
    s"""
       |//> using test.dep org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_testDepsKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using test.deps $CARET
       |""".stripMargin,
    s"""
       |//> using test.deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_testDependenciesKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using test.dependencies $CARET
       |""".stripMargin,
    s"""
       |//> using test.dependencies org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_compileOnlyDepKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using compileOnly.dep $CARET
       |""".stripMargin,
    s"""
       |//> using compileOnly.dep org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_compileOnlyDepsKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using compileOnly.deps $CARET
       |""".stripMargin,
    s"""
       |//> using compileOnly.deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_compileOnlyDependenciesKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using compileOnly.dependencies $CARET
       |""".stripMargin,
    s"""
       |//> using compileOnly.dependencies org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_nonDependencyKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using options $CARET
       |""".stripMargin,
    s"""
       |//> using options "org.scalatest" %% "scalatest" % "3.2.10"$CARET
       |""".stripMargin,
  )
  //endregion

  //region No modifications
  def testSimpleSbtDependency_doNotModifyPastedText_inDirectiveWithUnknownCommand(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> UnknownCommand $CARET
       |""".stripMargin,
    s"""
       |//> UnknownCommand "org.scalatest" %% "scalatest" % "3.2.10"$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_doNotModifyPastedText_inTheMiddleOfTheDirective(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using deps ${CARET}org.scalatest::scalatest:3.2.10
       |""".stripMargin,
    s"""
       |//> using deps "org.scalatest" %% "scalatest" % "3.2.10"${CARET}org.scalatest::scalatest:3.2.10
       |""".stripMargin,
  )

  def testSimpleSbtDependency_doNotModifyPastedText_InTheMiddleOfTheDependencyList(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, $CARET, org.scalatest::scalatest:3.2.10
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, "org.scalatest" %% "scalatest" % "3.2.10"$CARET, org.scalatest::scalatest:3.2.10
       |""".stripMargin,
  )

  def testSimpleSbtDependency_doNotModifyPastedText_InCommonComment(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |// $CARET
       |""".stripMargin,
    s"""
       |// "org.scalatest" %% "scalatest" % "3.2.10"$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_doNotModifyPastedText_InScalaCode(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |val dependency = $CARET
       |""".stripMargin,
    s"""
       |val dependency = "org.scalatest" %% "scalatest" % "3.2.10"$CARET
       |""".stripMargin,
  )

  def testNotDependency_doNotModifyPastedText(): Unit = doPasteTest(
    "val foo = 10",
    s"""
       |//> using dep $CARET
       |""".stripMargin,
    s"""
       |//> using dep val foo = 10$CARET
       |""".stripMargin,
  )

  // TODO(SCL-23704): support this case?
  def testDependencyWithParentheses_doNotModifyPastedText(): Unit = doPasteTest(
    """((("org.scalatest") %% "scalatest") % "3.2.10")""",
    s"""
       |//> using dep $CARET
       |""".stripMargin,
    s"""
       |//> using dep ((("org.scalatest") %% "scalatest") % "3.2.10")$CARET
       |""".stripMargin,
  )
  //endregion

  //region Dependencies with `libraryDependencies` prefix
  def testSimpleSbtDependency_withLibraryDependencies(): Unit = doPasteTest(
    """libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using dep $CARET
       |""".stripMargin,
    s"""
       |//> using dep org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_withLibraryDependencies_inEmptyDirective(): Unit = doPasteTest(
    """libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSeqOfSbtDependencies_withLibraryDependencies(): Unit = doPasteTest(
    """libraryDependencies ++= Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.10",
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSeqOfSbtDependencies_withLibraryDependenciesAssignment(): Unit = doPasteTest(
    """libraryDependencies := Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.10",
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSeqOfMultipleSbtDependencies_withLibraryDependencies(): Unit = doPasteTest(
    """libraryDependencies ++= Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.10",
      |  "org.apache.maven" % "maven-artifact" % "3.9.8",
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )
  //endregion

  //region Dependencies with a version as a reference
  def testSimpleSbtDependency_withVersionRef_invokeAutoCompletion(): Unit = {
    //noinspection ApiStatus
    DependencyUtil.updateMockVersionCompletionCache(("org.apache.maven", "maven-artifact") -> Seq("3.9.8"))

    doPasteTest(
      """"org.apache.maven" % "maven-artifact" % latestVersion""",
      s"""
         |//> using dep $CARET
         |""".stripMargin,
      s"""
         |//> using dep org.apache.maven:maven-artifact:$CARET
         |""".stripMargin
    )

    completionAutoPopupTester.joinAutopopup()
    completionAutoPopupTester.joinCompletion()

    invokeAndWait {
      scalaCompletionTestFixture.finishLookup(completionChar = Lookup.NORMAL_SELECT_CHAR) { lookupItem =>
        lookupItem.getLookupString == "org.apache.maven:maven-artifact:3.9.8"
      }
    }

    myFixture.checkResult(
      s"""
         |//> using dep org.apache.maven:maven-artifact:3.9.8$CARET
         |""".stripMargin.withNormalizedSeparator,
      true
    )
  }

  def testSeqOfMultipleSbtDependencies_withVersionRef(): Unit = doPasteTest(
    """libraryDependencies ++= Seq(
      |  "org.apache.maven" % "maven-artifact" % latestVersion,
      |  "org.apache.maven" % "maven-artifact" % latestVersion,
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.apache.maven:maven-artifact:latestVersion, org.apache.maven:maven-artifact:latestVersion$CARET
       |""".stripMargin,
  )

  def testSeqOfMultipleSbtDependencies_withVersionRefAtTheEnd(): Unit = doPasteTest(
    """libraryDependencies ++= Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.10",
      |  "org.apache.maven" % "maven-artifact" % latestVersion,
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, org.apache.maven:maven-artifact:latestVersion$CARET
       |""".stripMargin,
  )

  def testSeqOfMultipleSbtDependencies_withVersionRefAtTheStart(): Unit = doPasteTest(
    """libraryDependencies ++= Seq(
      |  "org.apache.maven" % "maven-artifact" % latestVersion,
      |  "org.scalatest" %% "scalatest" % "3.2.10",
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.apache.maven:maven-artifact:latestVersion, org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )
  //endregion

  //region Selection
  def testSimpleSbtDependency_selection_wholeDirectiveValue(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using dep ${START}some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//> using dep org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startBeforeCommand(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> ${START}using dep some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//> using deps org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_doNotModifyPastedText_startInsideCommand(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> usi${START}ng dep some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//> usi"org.apache.maven" % "maven-artifact" % "3.9.8"$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startAfterCommand(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using$START dep some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//> using deps org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startBeforeKey(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using ${START}dep some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//> using deps org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_doNotModifyPastedText_startInsideKey(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using de${START}p some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//> using de"org.apache.maven" % "maven-artifact" % "3.9.8"$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startAfterKey(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using dep$START some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//> using dep org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_doNotModifyPastedText_startInsidePrefix(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//$START> using dep some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |//"org.apache.maven" % "maven-artifact" % "3.9.8"$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_doNotModifyPastedText_startOutsideOfTheDirective(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |val foo $START= 10
       |//> using dep some:dependency:1.2.3$END
       |""".stripMargin,
    s"""
       |val foo "org.apache.maven" % "maven-artifact" % "3.9.8"$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startBeforeValue_endOutsideOfTheDirective(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using dep ${START}some:dependency:1.2.3
       |val foo = 10$END
       |""".stripMargin,
    s"""
       |//> using dep org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_doNotModifyPastedText_startInsideValue(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using dep some:${START}dependency:1.2.3
       |val foo = 10$END
       |""".stripMargin,
    s"""
       |//> using dep some:"org.apache.maven" % "maven-artifact" % "3.9.8"$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startAfterCommaAtTheEnd(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using dep some:dependency:1.2.3,$START
       |val foo = 10$END
       |""".stripMargin,
    s"""
       |//> using dep some:dependency:1.2.3, org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startBeforeCommaAtTheEnd(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using dep some:dependency:1.2.3$START,
       |val foo = 10$END
       |""".stripMargin,
    s"""
       |//> using dep some:dependency:1.2.3, org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startAfterPrefix_endOutsideOfTheDirective(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//>$START using dep some:dependency:1.2.3,
       |val foo = 10$END
       |""".stripMargin,
    s"""
       |//> using deps org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_selection_startBeforeNonDepKey(): Unit = doPasteTest(
    """"org.apache.maven" % "maven-artifact" % "3.9.8"""",
    s"""
       |//> using ${START}scala 2.13.14
       |val foo = 10$END
       |""".stripMargin,
    s"""
       |//> using deps org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )
  //endregion

  //region Dependency with configuration
  def testDependencyWithConfiguration_afterDepKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10" % Test""",
    s"""
       |//> using dep $CARET
       |""".stripMargin,
    s"""
       |//> using dep "org.scalatest" %% "scalatest" % "3.2.10" % Test$CARET
       |""".stripMargin,
  )

  def testDependencyWithConfiguration_inEmptyDirective(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10" % Test""",
    s"""
       |//> $CARET
       |""".stripMargin,
    s"""
       |//> "org.scalatest" %% "scalatest" % "3.2.10" % Test$CARET
       |""".stripMargin,
  )
  //endregion

  def testSimpleSbtDependency_withoutSpaces(): Unit = doPasteTest(
    """"org.scalatest"%%"scalatest"%"3.2.10"""",
    s"""
       |//> using dep $CARET
       |""".stripMargin,
    s"""
       |//> using dep org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_withTrailingSpacesAfterCaret(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using dep $CARET   ${""}
       |""".stripMargin,
    s"""
       |//> using dep org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_inDirectiveWithoutKey(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_inEmptyDirective(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_atTheEndOfExistingList(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, org.scalatest::scalatest:3.2.10, $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, org.scalatest::scalatest:3.2.10, org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSimpleSbtDependency_evenIfThereAreWhiteSpacesAtTheEnd(): Unit = doPasteTest(
    """"org.scalatest" %% "scalatest" % "3.2.10"""",
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, org.scalatest::scalatest:3.2.10, $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, org.scalatest::scalatest:3.2.10, org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSeqOfSbtDependencies(): Unit = doPasteTest(
    """Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.10",
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10$CARET
       |""".stripMargin,
  )

  def testSeqOfMultipleSbtDependencies(): Unit = doPasteTest(
    """Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.10",
      |  "org.apache.maven" % "maven-artifact" % "3.9.8",
      |)""".stripMargin,
    s"""
       |//> using deps $CARET
       |""".stripMargin,
    s"""
       |//> using deps org.scalatest::scalatest:3.2.10, org.apache.maven:maven-artifact:3.9.8$CARET
       |""".stripMargin,
  )
  //endregion
}
