package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.{GrazieCheckers, GrazieSpellCheckingInspection}
import com.intellij.grazie.text.TextChecker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.{ExtensionTestUtil, PlatformTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.reflect.{ClassTag, classTag}

/**
 * Implementation is inspired by `com.intellij.grazie.GrazieTestBase` from IntelliJ repo
 */
abstract class GrazieScalaTestBase extends ScalaLightCodeInsightFixtureTestCase:

  protected val additionalEnabledRules: Set[String] = Set.empty

  private lazy val inspectionTools = Array(GrazieInspection(), GrazieInspection.Grammar(), GrazieInspection.Style(), GrazieSpellCheckingInspection())
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

  override def getTestDataPath: String =
    new File(TestUtils.getTestDataPath + "/../../integration/textAnalysis/testData").getCanonicalPath

  override def setUp(): Unit =
    super.setUp()

    myFixture.enableInspections(inspectionTools*)

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
        /*enabledGrammarStrategies = */ state.getEnabledGrammarStrategies: @nowarn("cat=deprecation"),
        /*disabledGrammarStrategies = */ state.getDisabledGrammarStrategies: @nowarn("cat=deprecation"),
        /*enabledCommitIntegration = */ state.getEnabledCommitIntegration: @nowarn("cat=deprecation"),
        /*userDisabledRules = */ state.getUserDisabledRules,
        /*userEnabledRules = */ (enabledRules ++ additionalEnabledRules).asJava,
        /*domainDisabledRules = */ state.getDomainDisabledRules,
        /*domainEnabledRules = */ state.getDomainEnabledRules,
        /*suppressingContext = */ state.getSuppressingContext,
        /*detectionContext = */ state.getDetectionContext,
        /*checkingContext = */ checkingContext,
        /*version = */ state.getVersion,
        /*styleProfile = */ state.getStyleProfile,
        /*parameters = */ state.getParameters,
        /*parametersPerDomain = */ state.getParametersPerDomain,
        /*useOxfordSpelling = */ state.getUseOxfordSpelling,
        /*autoFix = */ state.getAutoFix
      )

    service[GrazieCheckers].awaitConfiguration()

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
      GrazieConfig.Companion.update { (_: GrazieConfig.State) =>
        new GrazieConfig.State()
      }

      service[GrazieCheckers].awaitConfiguration()
    catch case e: Throwable =>
      addSuppressedException(e)
    finally
      super.tearDown()
  end tearDown

  protected def runHighlightTestForFile(fileName: String): Unit =
    myFixture.configureByFile(fileName)
    myFixture.checkHighlighting(true, false, false)
end GrazieScalaTestBase

inline def service[T: ClassTag]: T =
  ApplicationManager.getApplication
    .getService(classTag[T].runtimeClass.asInstanceOf[Class[T]])
