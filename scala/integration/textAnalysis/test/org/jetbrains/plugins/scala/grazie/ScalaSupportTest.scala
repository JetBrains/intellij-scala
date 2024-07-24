package org.jetbrains.plugins.scala.grazie

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable

/**
 * This class is copied from IntelliJ repo from `com.intellij.grazie.ide.language.JavaSupportTest`.
 * Test data was adapted to Scala syntax.
 * When tests fail, you might look into the corresponding changes in Java tests first.
 */
class ScalaSupportTest extends GrazieScalaTestBase:

  override val additionalEnabledRules: Set[String] = Set("LanguageTool.EN.UPPERCASE_SENTENCE_START")

  override def getTestDataPath: String =
    super.getTestDataPath() + "/ide/language/scala"

  def testSpellcheckInConstructs(): Unit =
    runHighlightTestForFile("Constructs.scala")

  def testGrammarCheckInComments(): Unit =
    runHighlightTestForFile("Comments.scala")

  def testGrammarCheckInDocs(): Unit =
    runHighlightTestForFile("Docs.scala")

  def testGrammarCheckInStringLiterals(): Unit =
    runHighlightTestForFile("StringLiterals.scala")

  def testSplitLineQuickFix(): Unit =
    runHighlightTestForFile("SplitLine.scala")
    myFixture.launchAction(myFixture.findSingleIntention(", but"))
    myFixture.checkResultByFile("SplitLine_after.scala")

  def testDoNotMergeTextWithNonText(): Unit =
    runHighlightTestForFile("AccidentalMerge.scala")
    myFixture.launchAction(myFixture.getAvailableIntentions().stream().filter(_.getText == "Remove").findFirst().get())
    myFixture.checkResultByFile("AccidentalMerge_after.scala")

  def testPerformance_LongComment(): Unit =
    //NOTE: don't use lambda due to Scala 3/JUnit integration issue https://github.com/scala/scala3/issues/20322
    runPerformanceTest(new ThrowableRunnable[Throwable]() {
      override def run(): Unit = {
        runHighlightTestForFile("LongCommentPerformance.scala")
      }
    })

  def testPerformance_ManyLineComments(): Unit =
    val text = "// this is a single line comment\n" * 5000
    myFixture.configureByText("a.scala", text)

    //NOTE: don't use lambda due to Scala 3/JUnit integration issue https://github.com/scala/scala3/issues/20322
    runPerformanceTest(new ThrowableRunnable[Throwable]() {
      override def run(): Unit = {
        myFixture.checkHighlighting()
      }
    })

  private def runPerformanceTest(runnable: ThrowableRunnable[_]): Unit =
    PlatformTestUtil
      .newBenchmark("highlighting", () => runnable.run())
      .setup { () => getPsiManager.dropPsiCaches() }
      .start()
end ScalaSupportTest