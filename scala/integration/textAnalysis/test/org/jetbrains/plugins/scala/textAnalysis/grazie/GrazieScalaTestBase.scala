package org.jetbrains.plugins.scala.textAnalysis.grazie

import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.rules.settings.TextStyle
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.HunspellDescriptor
import com.intellij.grazie.spellcheck.{GrazieCheckers, GrazieSpellCheckingInspection}
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.grazie.{GrazieConfig, GrazieDynamic}
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.testFramework.{ExtensionTestUtil, PlatformTestUtil}
import com.intellij.util.io.ZipUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File
import java.nio.file.{Files, Path}
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*
import scala.reflect.{ClassTag, classTag}

//noinspection ApiStatus,UnstableApiUsage
abstract class GrazieScalaTestBase extends ScalaLightCodeInsightFixtureTestCase:

  import GrazieScalaTestBase.*

  protected val additionalEnabledRules: Set[String] = Set.empty

  protected val additionalEnabledContextLanguages: Set[Language] = Set.empty

  protected val enableGrazieChecker: Boolean = false

  override def getTestDataPath: String =
    File(TestUtils.getTestDataPath + "/../../integration/textAnalysis/testData").getCanonicalPath

  override def setUp(): Unit =
    super.setUp()
    maskSaxParserFactory(getTestRootDisposable)
    if enableGrazieChecker then
      Registry.get("spellchecker.grazie.enabled").setValue(true, getTestRootDisposable)
    myFixture.enableInspections(inspectionTools *)

    enableProofreadingFor(enabledLanguages)

    val newExtensions = TextChecker.allCheckers.stream.map:
      case _: LanguageToolChecker => LanguageToolChecker.TestChecker()
      case checker => checker
    .toList

    ExtensionTestUtil.maskExtensions(
      ExtensionPointName.create[TextChecker]("com.intellij.grazie.textChecker"),
      newExtensions,
      getTestRootDisposable
    )

  override def tearDown(): Unit =
    try
      GrazieConfig.Companion.update((_: GrazieConfig.State) => GrazieConfig.State())
      service[GrazieCheckers].awaitConfiguration()
      unloadLangs(getProject)
    catch
      case e: Throwable =>
        addSuppressedException(e)
    finally
      super.tearDown()

  protected def enableProofreadingFor(languages: Set[Lang]): Unit =
    // Load langs manually to prevent potential deadlock
    val enabledLanguages = languages union service[GrazieConfig].getState.getEnabledLanguages.asScala
    loadLangs(enabledLanguages, getProject)

    GrazieConfig.Companion.update: (state: GrazieConfig.State) =>
      val context = state.getCheckingContext
      val checkingContext = context.copy(
        /*isCheckInCommitMessagesEnabled = */ context.isCheckInCommitMessagesEnabled,
        /*isCheckInStringLiteralsEnabled = */ true,
        /*isCheckInCommentsEnabled = */ true,
        /*isCheckInDocumentationEnabled = */ true,
        /*disabledLanguages = */ context.getDisabledLanguages,
        /*enabledLanguages = */ additionalEnabledContextLanguages.map(_.getID).asJava
      )
      import TextStyleDomain.*
      val domains = Set(Commit, AIPrompt, CodeDocumentation, CodeComment)
      val domainEnabledRules =
        domains.map(domain => domain -> (enabledRules union additionalEnabledRules).asJava).toMap.asJava
      state.copy(
        /*enabledLanguages = */ enabledLanguages.asJava,
        /*enabledGrammarStrategies = */ state.getEnabledGrammarStrategies: @nowarn("cat=deprecation"),
        /*disabledGrammarStrategies = */ state.getDisabledGrammarStrategies: @nowarn("cat=deprecation"),
        /*enabledCommitIntegration = */ state.getEnabledCommitIntegration: @nowarn("cat=deprecation"),
        /*userDisabledRules = */ state.getUserDisabledRules,
        /*userEnabledRules = */ (enabledRules ++ additionalEnabledRules).asJava,
        /*domainDisabledRules = */ state.getDomainDisabledRules,
        /*domainEnabledRules = */ domainEnabledRules,
        /*suppressingContext = */ state.getSuppressingContext,
        /*detectionContext = */ state.getDetectionContext,
        /*checkingContext = */ checkingContext,
        /*version = */ state.getVersion,
        /*styleProfile = */ TextStyle.Unspecified.id(),
        /*parameters = */ state.getParameters,
        /*parametersPerDomain = */ state.getParametersPerDomain,
        /*useOxfordSpelling = */ state.getUseOxfordSpelling,
        /*autoFix = */ state.getAutoFix,
        /*explicitlyChosenProcessing = */ state.getExplicitlyChosenProcessing
      )

    service[GrazieCheckers].awaitConfiguration()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

  protected def runHighlightTestForFile(file: String): Unit =
    myFixture.configureByFile(file)
    myFixture.checkHighlighting(/* checkWarnings = */ true, /* checkInfos = */ false, /* checkWeakWarnings = */ false)

//noinspection ApiStatus,UnstableApiUsage
private object GrazieScalaTestBase:
  lazy val inspectionTools: Array[LocalInspectionTool] =
    Array(GrazieInspection(), GrazieInspection.Grammar(), GrazieInspection.Style(), GrazieSpellCheckingInspection())

  /**
   * To speed up test execution, only English is enabled by default.
   *
   * Please use [[GrazieScalaTestBase.enableProofreadingFor]] if a test requires a specific language.
   */
  val enabledLanguages: Set[Lang] = Set(Lang.AMERICAN_ENGLISH)
  val enabledRules: Set[String] = Set("LanguageTool.EN.COMMA_WHICH", "LanguageTool.EN.UPPERCASE_SENTENCE_START", "LanguageTool.DE.MANNSTUNDE")
  val hunspellLangs: Set[Lang] = Set(Lang.GERMANY_GERMAN, Lang.AUSTRIAN_GERMAN, Lang.SWISS_GERMAN, Lang.RUSSIAN)

  def maskSaxParserFactory(disposable: Disposable): Unit =
    val saxParserKey = "javax.xml.parsers.SAXParserFactory"
    val oldSaxParserFactory = System.setProperty(saxParserKey, "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl")
    val child: Disposable = () =>
      if oldSaxParserFactory != null then
        System.setProperty(saxParserKey, oldSaxParserFactory)
      else
        System.clearProperty(saxParserKey)
    end child
    Disposer.register(disposable, child)

  def loadLangs(langs: Set[Lang], project: Project): Unit =
    langs.filter(hunspellLangs).foreach(loadLang(_, project))

  def unloadLangs(project: Project): Unit =
    hunspellLangs.foreach(l => unloadLang(l.getIso, project))

  private def loadLang(lang: Lang, project: Project): Unit =
    val zipPath = PathManager.getResourceRoot(
      classOf[PathManager].getClassLoader,
      s"dictionary/${lang.getIso.name().toLowerCase}.aff"
    )
    if zipPath == null then
      throw AssertionError(s"Hunspell-${lang.getIso} not found in classpath")
    val zip = Path.of(zipPath)
    if !zip.exists then
      throw AssertionError(s"Hunspell-${lang.getIso} not found in classpath")
    val hunspellRemote = lang.getHunspellRemote
    if hunspellRemote == null then
      throw AssertionError(s"Hunspell remote for language ${lang.getIso} not found")
    val outputDir = GrazieDynamic.INSTANCE.getLangDynamicFolder(lang).resolve(hunspellRemote.getStorageName)
    Files.createDirectories(outputDir)
    ZipUtil.extract(zip, outputDir, HunspellDescriptor.Companion.filenameFilter())
    val spellChecker = SpellCheckerManager.getInstance(project).getSpellChecker
    if spellChecker == null then
      throw AssertionError("Could not get a spell checker instance for the test project")
    val dictionary = lang.getDictionary
    if dictionary == null then
      throw AssertionError(s"Lang ${lang.getIso} does not have an instance of a dictionary")
    spellChecker.addDictionary(dictionary)
  end loadLang

  private def unloadLang(iso: LanguageISO, project: Project): Unit =
    val lang = Lang.getEntries.toArray(Array.ofDim[Lang](_)).find(_.getIso == iso).get
    SpellCheckerManager.getInstance(project).removeDictionary(getDictionaryPath(lang))

  private def getDictionaryPath(lang: Lang): String =
    val hunspellRemote = lang.getHunspellRemote
    if hunspellRemote == null then
      throw AssertionError(s"Hunspell remote for language ${lang.getIso} not found")
    GrazieDynamic.INSTANCE.getLangDynamicFolder(lang).resolve(hunspellRemote.getFile).toString

  inline def service[T](using ClassTag[T]): T =
    ApplicationManager.getApplication.getService(classTag[T].runtimeClass.asInstanceOf[Class[T]])
