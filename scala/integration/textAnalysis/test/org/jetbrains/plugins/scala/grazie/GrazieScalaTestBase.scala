package org.jetbrains.plugins.scala.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextChecker
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.{ExtensionTestUtil, PlatformTestUtil}
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.SetHasAsJava

/**
 * Implementation is inspired by `com.intellij.grazie.GrazieTestBase` from IntelliJ repo
 */
abstract class GrazieScalaTestBase extends ScalaLightCodeInsightFixtureTestCase:

  protected val additionalEnabledRules: Set[String] = Set.empty

  private lazy val inspectionTools = Array(new GrazieInspection(), new SpellCheckingInspection())
  private val enabledLanguages: Set[Lang] = Set(
    Lang.AMERICAN_ENGLISH,
    Lang.GERMANY_GERMAN,
    Lang.RUSSIAN,
    Lang.ITALIAN
  )
  private val enabledRules = Set(
    "LanguageTool.EN.COMMA_WHICH",
    "LanguageTool.EN.UPPERCASE_SENTENCE_START"
  )

  override def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries(this)

  override def getTestDataPath: String =
    new File(TestUtils.getTestDataPath + "/../../integration/textAnalysis/testData").getCanonicalPath

  override def setUp(): Unit =
    super.setUp()

    myFixture.enableInspections(inspectionTools: _*)

    GrazieConfig.Companion.update: (state: GrazieConfig.State) =>
      val context = state.getCheckingContext
      val checkingContext = context.copy(
        /*isCheckInCommitMessagesEnabled = */ context.isCheckInCommitMessagesEnabled,
        /*isCheckInStringLiteralsEnabled = */ true,
        /*isCheckInCommentsEnabled = */ true,
        /*isCheckInDocumentationEnabled = */ true,
        /*disabledLanguages = */ context.getDisabledLanguages,
        /*enabledLanguages = */ context.getEnabledLanguages,
      )
      state.copy(
        /*enabledLanguages = */ enabledLanguages.asJava,
        /*enabledGrammarStrategies = */ state.getEnabledGrammarStrategies,
        /*disabledGrammarStrategies = */ state.getDisabledGrammarStrategies,
        /*enabledCommitIntegration = */ state.getEnabledCommitIntegration,
        /*userDisabledRules = */ state.getUserDisabledRules,
        /*userEnabledRules = */ (enabledRules ++ additionalEnabledRules).asJava,
        /*suppressingContext = */ state.getSuppressingContext,
        /*detectionContext = */ state.getDetectionContext,
        /*checkingContext = */ checkingContext,
        /*version = */ state.getVersion,
      ): @nowarn("cat=deprecation")

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val newExtensions = TextChecker.allCheckers.stream.map:
      case checker: LanguageToolChecker => new LanguageToolChecker.TestChecker()
      case checker => checker
    .toList

    ExtensionTestUtil.maskExtensions(
      ExtensionPointName.create[TextChecker]("com.intellij.grazie.textChecker"),
      newExtensions,
      getTestRootDisposable
    )
  end setUp

  override def tearDown(): Unit =
    try
      GrazieConfig.Companion.update { (state: GrazieConfig.State) =>
        new GrazieConfig.State()
      }
    catch case e: Throwable =>
      addSuppressedException(e)
    finally
      super.tearDown()
  end tearDown

  protected def runHighlightTestForFile(fileName: String): Unit =
    myFixture.configureByFile(fileName)
    myFixture.checkHighlighting(true, false, false)
end GrazieScalaTestBase