package org.jetbrains.plugins.scala.testingSupport.test.testdata

import java.{util => ju}

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.{CommonProgramRunConfigurationParameters, ExternalizablePath, ShortenCommandLine}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.util.xmlb.{Accessor, XmlSerializer}
import org.apache.commons.lang3.StringUtils
import org.jdom.Element
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.testingSupport.TestWorkingDirectoryProvider
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, SearchForTest, TestKind, exceptions}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._

/**
 * NOTE: when changing constructor params do not forget to edit TestConfigurationData.serializeIntoSkippingDefaults
 * TODO: make this class anemic, with default constructor, just containing serialized values
 */
//noinspection ConvertNullInitializerToUnderscore
abstract class TestConfigurationData(config: AbstractTestRunConfiguration)
  extends CommonProgramRunConfigurationParameters
    with exceptions {

  type SelfType <: TestConfigurationData

  def getKind: TestKind
  def checkSuiteAndTestName: CheckResult
  def getTestMap: Map[String, Set[String]]

  type CheckResult = Either[RuntimeConfigurationException, Unit]

  protected final def getModule: Module = config.getModule
  final def getProject: Project = config.getProject
  protected final def checkModule: CheckResult =
    if (getModule != null) Right(())
    else Left(configurationException(ScalaBundle.message("test.run.config.module.is.not.specified")))

  protected final def check(condition: Boolean, exception: => RuntimeConfigurationException): CheckResult =
    Either.cond(condition, (), exception)

  final def searchTestsInWholeProject: Boolean =
    getKind == TestKind.ALL_IN_PACKAGE && searchTest == SearchForTest.IN_WHOLE_PROJECT

  def copyFieldsFromForm(form: TestRunConfigurationForm): Unit = {
    setSearchTest(form.getSearchForTest)
    setJavaOptions(form.getJavaOptions)
    setTestArgs(form.getTestArgs)
    setJrePath(form.getJrePath)
    setShowProgressMessages(form.getShowProgressMessages)
    setUseSbt(form.getUseSbt)
    setUseUiWithSbt(form.getUseUiWithSbt)
    setWorkingDirectory(form.getWorkingDirectory)
    setShortenClasspath(form.getShortenCommandLine)
    envs = form.getEnvironmentVariables
  }

  final def copyCommonFieldsFrom(other: TestConfigurationData): Unit = {
    setSearchTest(other.searchTest)
    setJavaOptions(other.javaOptions)
    setTestArgs(other.testArgs)
    setJrePath(other.jrePath)
    setShowProgressMessages(other.showProgressMessages)
    setUseSbt(other.useSbt)
    setUseUiWithSbt(other.useUiWithSbt)
    setWorkingDirectory(other.getWorkingDirectory)
    setShortenClasspath(other.shortenClasspath)
    envs = new java.util.HashMap(other.envs)
  }

  /** NOTE: overridden implementations must call super method */
  protected def copyFieldsFrom(data: SelfType): Unit =
    copyCommonFieldsFrom(data)

  def copy(config: AbstractTestRunConfiguration): TestConfigurationData

  def writeExternal(element: Element): Unit = {
//    XmlSerializer.serializeInto(this, element)
    TestConfigurationData.serializeIntoSkippingDefaults(this, element)
  }

  def readExternal(element: Element): Unit  = {
    XmlSerializer.deserializeInto(this, element)
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateBool("useUiWithSbt")(useUiWithSbt = _)
      helper.migrateBool("showProgressMessages")(showProgressMessages = _)
      helper.migrateBool("useSbt")(useSbt = _)
      helper.migrateString("vmparams")(javaOptions = _)
      helper.migrateString("params")(testArgs = _)
      helper.migrateString("searchForTest")(x => searchTest = SearchForTest.parse(x))
      helper.migrateMap("envs", "envVar", envs)
      helper.migrateString("workingDirectory")(workingDirectory =_)
      helper.migrateString("jrePath")(jrePath =_)
    }
  }

  protected final def isDumb: Boolean = DumbService.isDumb(config.getProject)

  // Bean settings:
  @BeanProperty var searchTest          : SearchForTest                 = SearchForTest.ACCROSS_MODULE_DEPENDENCIES
  @BeanProperty var showProgressMessages: Boolean                       = true // TODO: there already exists a parameter in Logs tab, do we need this parameter?
  @BeanProperty var useSbt              : Boolean                       = false
  @BeanProperty var useUiWithSbt        : Boolean                       = false
  @BeanProperty var jrePath             : String                        = _
  @BeanProperty var testArgs            : String                        = ""
  @BeanProperty var javaOptions         : String                        = ""
  @BeanProperty var envs                : java.util.Map[String, String] = new java.util.HashMap[String, String]()
  @BeanProperty var shortenClasspath    : ShortenCommandLine            = null // null is valid value, see ConfigurationWithCommandLineShortener doc

  private var _passParentEnvs: Boolean = false // TODO: use

  private var workingDirectory: String     = ""
  def setWorkingDirectory(s: String): Unit = workingDirectory = ExternalizablePath.urlValue(s)
  def getWorkingDirectory: String          = ExternalizablePath.localPathValue(workingDirectory)

  def initWorkingDirIfEmpty(): Unit =
    if (StringUtils.isBlank(workingDirectory)) {
      val workingDir = Option(getModule).flatMap(moduleWorkingDirectory).orElse(projectWorkingDirectory)
      setWorkingDirectory(workingDir.getOrElse(""))
    }

  private def moduleWorkingDirectory(module: Module): Option[String] = {
    val providers = TestWorkingDirectoryProvider.implementations
    providers.iterator.map(_.getWorkingDirectory(module)).find(_.isDefined).flatten
  }

  private def projectWorkingDirectory: Option[String] =
    Option(getProject.baseDir).map(_.getPath)

  override def setProgramParameters(value: String): Unit = testArgs = value
  override def getProgramParameters: String = testArgs
  override def setPassParentEnvs(passParentEnvs: Boolean): Unit = _passParentEnvs = passParentEnvs
  override def isPassParentEnvs: Boolean = _passParentEnvs
}

object TestConfigurationData {

  def createFromExternal(element: Element, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
    val testData = create(configuration.testKind, configuration)
    testData.readExternal(element)
    testData
  }

  def createFromForm(form: TestRunConfigurationForm, configuration: AbstractTestRunConfiguration): TestConfigurationData = {
    val testData = create(form.getTestKind, configuration)
    testData.copyFieldsFromForm(form)
    testData
  }

  private def create(testKind: TestKind, configuration: AbstractTestRunConfiguration) = testKind match {
    case TestKind.ALL_IN_PACKAGE => new AllInPackageTestData(configuration)
    case TestKind.CLAZZ          => new ClassTestData(configuration)
    case TestKind.TEST_NAME      => new SingleTestData(configuration)
    case TestKind.REGEXP         => new RegexpTestData(configuration)
    case null                    => new ClassTestData(configuration) // null can be set by IDEA internally during intermediate xml read/write
  }

  private def serializeIntoSkippingDefaults(data: TestConfigurationData, element: Element): Unit = {
    // ATTENTION: assuming that config isn't used during construction! ideally should remove config constructor parameter
    val constructor = data.getClass.getConstructor(classOf[AbstractTestRunConfiguration])
    val defaultData = constructor.newInstance(null)

    XmlSerializer.serializeInto(data, element, (accessor: Accessor, bean: Any) => {
      val value        = accessor.read(bean)
      val defaultValue = accessor.read(defaultData)

      val skip = (value, defaultValue) match {
        case (list1: ju.List[_], list2: ju.List[_])   => list1.asScala == list2.asScala
        case (map1: ju.Map[_, _], map2: ju.Map[_, _]) => map1.asScala == map2.asScala
        case _                                        => value == defaultValue
      }
      !skip
    })
  }
}
