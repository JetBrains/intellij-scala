package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext, LazyRunConfigurationProducer}
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.{NlsSafe, Ref}
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions.{LoggerExt, ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo.{AllInPackage, ClassWithTestName}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer._
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{SingleTestData, _}

import scala.jdk.CollectionConverters._

abstract class AbstractTestConfigurationProducer[T <: AbstractTestRunConfiguration]
  extends LazyRunConfigurationProducer[T] {

  final type PsiElementLocation = Location[_ <: PsiElement]

  protected def suitePaths: Seq[String]

  private def hasTestSuitesInModuleDependencies(module: Module): Boolean = {
    val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
    val psiManager = ScalaPsiManager.instance(module.getProject)
    val exists = suitePaths.exists(psiManager.getCachedClass(scope, _).isDefined)
    exists
  }

  override def setupConfigurationFromContext(
    configuration: T,
    context: ConfigurationContext,
    sourceElement: Ref[PsiElement]
  ): Boolean = {
    Log.traceSafe(
      s"setupConfigurationFromContext start:" +
        s" ${Option(sourceElement.get()).map(el => {el.toString + ", " + el.getText}).orNull}"
    )

    val elementWithConfSettings = for {
      contextLocation <- Option(context.getLocation).toRight("context location is empty")
      contextModule   <- Option(contextLocation.getModule).toRight("context module is empty")
      _               <- ensure(!sourceElement.isNull, "source element is empty")
      _               <- ensure(hasTestSuitesInModuleDependencies(contextModule), "context module doesn't contain any test dependencies")
      elementWithConf <- createConfigurationFromContextLocation(contextLocation)

      (testElement, confSettings) = elementWithConf
      config = confSettings.getConfiguration.asInstanceOf[T]
      _ <- ensure(isRunPossibleFor(config, testElement), "run is impossible")
    } yield (testElement, config)

    elementWithConfSettings match {
      case Left(failureReason)  =>
        Log.traceSafe(s"setupConfigurationFromContext failed: $failureReason")
        false
      case Right((testElement, config)) =>
        Log.traceSafe(s"setupConfigurationFromContext end: ${config.getName}")
        sourceElement.set(testElement)
        configuration.initFrom(config)
        true
    }
  }

  private def ensure(bool: Boolean, errorMessage: => String): Either[String, Unit] =
    if (bool) Right(()) else Left(errorMessage)

  private def extendCreatedConfiguration(configuration: RunConfigurationBase[_], location: PsiElementLocation): Unit = {
    val instance = JavaRunConfigurationExtensionManager.getInstance
    instance.extendCreatedConfiguration(configuration, location)
  }

  /**
   * @return element ~ test class OR test package OR test directory
   */
  @TestOnly
  def createConfigurationFromContextLocation(
    location: PsiElementLocation
  ): Either[String, (PsiElement, RunnerAndConfigurationSettings)] =
    for {
      contextInfo <- getContextInfo(location).toRight("couldn't prepare context info")
    } yield {
      val displayName = configurationName(contextInfo)
      val settings = RunManager.getInstance(location.getProject).createConfiguration(displayName, getConfigurationFactory)

      val configuration = settings.getConfiguration.asInstanceOf[T]

      configuration.testConfigurationData = getUpdatedTestData(configuration, contextInfo)
      configuration.testKind = configuration.testConfigurationData.getKind

      configuration.setGeneratedName(configuration.getName)

      if (configuration.getModule == null) {
        val jvmModule = location.getModule.findJVMModule
        jvmModule.foreach(configuration.setModule)
      }

      extendCreatedConfiguration(configuration, location)

      val element = contextInfo match {
        case _: AllInPackage         =>
          // if original was directory, return directory, not sure if it affects anything
          location.getPsiElement
        case info: ClassWithTestName =>
          info.testClass
      }
      (element, settings)
    }

  @NlsSafe
  protected def configurationName(contextInfo: CreateFromContextInfo): String

  protected def getContextInfo(location: PsiElementLocation): Option[CreateFromContextInfo] = {
    val psiElement = location.getPsiElement
    if (psiElement.is[PsiPackage, PsiDirectory])
      getTestPackageWithPackageName(location)
    else
      getTestClassWithTestName(location)
  }

  protected def getTestPackageWithPackageName(location: PsiElementLocation): Option[CreateFromContextInfo.AllInPackage] =
    location.getPsiElement match {
      case dir: PsiDirectory => Option(AbstractJavaTestConfigurationProducer.checkPackage(dir)).map(p => AllInPackage(p, dir.name))
      case pack: PsiPackage  => Some(AllInPackage(pack, pack.name))
      case _                 => None
    }

  def getTestClassWithTestName(location: PsiElementLocation): Option[ClassWithTestName]

  private def getUpdatedTestData(
    configuration: T,
    contextInfo: CreateFromContextInfo
  ): TestConfigurationData = {
    val testDataOld = configuration.testConfigurationData
    val testDataNew = contextInfo match {
      case AllInPackage(testPackage, _)                                =>
        // TODO: take name from context info 2nd parameter maybe?
        AllInPackageTestData(configuration, sanitize(testPackage.getQualifiedName))
      case ClassWithTestName(testClass, Some(testName)) if StringUtils.isNotBlank(testName) =>
        SingleTestData(configuration, sanitize(testClass.qualifiedName), testName)
      case ClassWithTestName(testClass, _)                             =>
        ClassTestData(configuration, sanitize(testClass.qualifiedName))
    }
    testDataNew.copyCommonFieldsFrom(testDataOld)
    testDataNew.initWorkingDirIfEmpty()
    testDataNew
  }

  /**
   * We check `hasTestSuitesInModuleDependencies` once again because configuration is only created
   * when the classpath of the module from the context contains test suite class.
   * However the created configuration can contain another module, defined in the template.
   * For that case we do not want to hide  'Create Test Configuration' item in context menu.
   * Instead, we allow opening "create configuration" dialog and show the error there, in the bottom of the dialog.
   *
   * @param testElement test class OR package OR directory
   */
  private def isRunPossibleFor(configuration: T, testElement: PsiElement): Boolean =
    testElement match {
      case cl: PsiClass if hasTestSuitesInModuleDependencies(configuration.getModule) =>
        val isValidSuite = configuration.isValidSuite(cl)
        isValidSuite || {
          // TODO: check with debugger: this shouldn't be true if previous is false cause in previous check we already checked inheritors?
          //  plus this seems to be not the optimal, cause inside configuration.isValidSuite we do quite a lot of job
          ClassInheritorsSearch.search(cl).asScala.exists(configuration.isValidSuite)
        }
      case _ => true
    }

  override def onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable): Unit = {
    // if some class is invalid (e.g. it's abstract class BaseTest) we offer user to choosing some inheritor test to run
    val handled = configuration.getConfiguration match {
      case config: AbstractTestRunConfiguration =>
        config.testConfigurationData match {
          case testData: ClassTestData =>
            val testClass = testData.getClassPathClazz
            if (config.isValidSuite(testClass))
              false
            else {
              val inheritorsChooser = new MyInheritorChooser(config, testData)
              inheritorsChooser.runMethodInAbstractClass(context, startRunnable, null, testClass)
            }
          case _ => false
        }
      case _ => false
    }

    if (!handled)
      startRunnable.run()
  }

  override final def isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean = {
    val location = context.getLocation
    if (location == null)
      configuration.testConfigurationData match {
        case _: RegexpTestData | _: AllInPackageTestData => sameModules(configuration, context)
        case _: ClassTestData                            => false
      }
    else if (TestConfigurationUtil.isPackageConfiguration(location.getPsiElement, configuration))
      true
    else
      isClassOfTestConfigurationFromLocation(configuration, location)
  }

  protected def isClassOfTestConfigurationFromLocation(configuration: T, location: PsiElementLocation): Boolean = {
    val classWithTestName = getTestClassWithTestName(location)
    classWithTestName.fold(false) { case ClassWithTestName(testClass, testName) =>
      val testClassPath = testClass.qualifiedName
      configuration.testConfigurationData match {
        case testData: SingleTestData => testData.testClassPath == testClassPath && testName.contains(testData.testName)
        case classData: ClassTestData => classData.testClassPath == testClassPath && testName.isEmpty
        case _                        => false
      }
    }
  }

  private def sameModules(configuration: T, context: ConfigurationContext) = {
    val configModule: Module = configuration.getConfigurationModule.getModule
    configModule == context.getModule || {
      val configTemplate = context.getRunManager.getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[T]
      configModule == configTemplate.getConfigurationModule.getModule
    }
  }
}

object AbstractTestConfigurationProducer {

  private final val Log: Logger = Logger.getInstance(classOf[AbstractTestConfigurationProducer[_]])

  // do not display backticks in test class/package name
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")

  sealed trait CreateFromContextInfo
  object CreateFromContextInfo {
    case class AllInPackage(testPackage: PsiPackage, packageName: String) extends CreateFromContextInfo
    case class ClassWithTestName(testClass: ScTypeDefinition, testName: Option[String]) extends CreateFromContextInfo
  }
}
