package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.ThrowableRunnable

/**
 * This class is copied from IntelliJ repo from `com.intellij.grazie.ide.language.JavaSupportTest`.
 * Test data was adapted to Scala syntax.
 * When tests fail, you might look into the corresponding changes in Java tests first.
 */
class ScalaSupportTest extends GrazieTest_WithScalaSdkAndJdk:

  override def getTestDataPath: String =
    super.getTestDataPath() + "/ide/language/scala"

  def testSpellcheckInConstructs(): Unit =
    runHighlightTestForFile("Constructs.scala")

  def testGrammarCheckInComments(): Unit =
    enableProofreadingFor(java.util.Set.of(Lang.GERMANY_GERMAN))
    runHighlightTestForFile("Comments.scala")

  def testGrammarCheckInDocs(): Unit =
    enableProofreadingFor(java.util.Set.of(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("Docs.scala")

  def testGrammarCheckInStringLiterals(): Unit =
    runHighlightTestForFile("StringLiterals.scala")

  def testSplitLineQuickFix(): Unit =
    runHighlightTestForFile("SplitLine.scala")
    val action = myFixture.findSingleIntention(", but")
    myFixture.launchAction(action)
    myFixture.checkResultByFile("SplitLine_after.scala")

  def testDoNotMergeTextWithNonText(): Unit =
    runHighlightTestForFile("AccidentalMerge.scala")
    myFixture.launchAction(myFixture.getAvailableIntentions().stream().filter(_.getText == "Remove").findFirst().get())
    myFixture.checkResultByFile("AccidentalMerge_after.scala")

  @PerformanceUnitTest
  def testPerformance_LongComment(): Unit =
    //NOTE: don't use lambda due to Scala 3/JUnit integration issue https://github.com/scala/scala3/issues/20322
    runPerformanceTest(new ThrowableRunnable[Throwable]() {
      override def run(): Unit = {
        runHighlightTestForFile("LongCommentPerformance.scala")
      }
    })

  @PerformanceUnitTest
  def testPerformance_ManyLineComments(): Unit =
    val text = "// this is a single line comment\n" * 5000
    myFixture.configureByText("a.scala", text)

    //NOTE: don't use lambda due to Scala 3/JUnit integration issue https://github.com/scala/scala3/issues/20322
    runPerformanceTest(new ThrowableRunnable[Throwable]() {
      override def run(): Unit = {
        myFixture.checkHighlighting()
      }
    })

  private def runPerformanceTest(runnable: ThrowableRunnable[?]): Unit =
    Benchmark.newBenchmark("highlighting", runnable)
      .setup { () => getPsiManager.dropPsiCaches() }
      .start()
end ScalaSupportTest
