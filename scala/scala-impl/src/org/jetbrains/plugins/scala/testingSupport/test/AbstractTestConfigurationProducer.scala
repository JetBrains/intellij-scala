package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext, LazyRunConfigurationProducer}
import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer._
import org.jetbrains.plugins.scala.testingSupport.test.testdata._

import scala.collection.JavaConverters._

abstract class AbstractTestConfigurationProducer[T <: AbstractTestRunConfiguration]
  extends LazyRunConfigurationProducer[T] {

  final type PsiElementLocation = Location[_ <: PsiElement]

  protected def suitePaths: Seq[String]

  private def hasTestSuitesInModuleDependencies(context: ConfigurationContext): Boolean =
    context.getModule match {
      case null   => false
      case module => hasTestSuitesInModuleDependencies(module)
    }

  private def hasTestSuitesInModuleDependencies(config: T): Boolean =
    config.getModule match {
      case null   => false
      case module => hasTestSuitesInModuleDependencies(module)
    }

  private def hasTestSuitesInModuleDependencies(module: Module): Boolean = {
    val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
    val psiManager = ScalaPsiManager.instance(module.getProject)
    suitePaths.exists(psiManager.getCachedClass(scope, _).isDefined)
  }

  // TODO: avoid nulls
  def getTestClassWithTestName(location: PsiElementLocation): (ScTypeDefinition, String)

  override def setupConfigurationFromContext(
    configuration: T,
    context: ConfigurationContext,
    sourceElement: Ref[PsiElement]
  ): Boolean = {
    def setup(
      testElement: PsiElement,
      confSettings: RunnerAndConfigurationSettings
    ): Boolean = {
      val configWithModule = configuration.clone.asInstanceOf[T]
      val cfg = confSettings.getConfiguration.asInstanceOf[T]
      configWithModule.setModule(cfg.getModule)

      val runIsPossible = isRunPossibleFor(configWithModule, testElement)
      if (runIsPossible) {
        sourceElement.set(testElement)

        configuration.setGeneratedName(cfg.suggestedName)
        configuration.setFileOutputPath(cfg.getOutputFilePath)
        configuration.setModule(cfg.getModule)
        configuration.setName(cfg.getName)
        configuration.setNameChangedByUser(!cfg.isGeneratedName)
        configuration.setSaveOutputToFile(cfg.isSaveOutputToFile)
        configuration.setShowConsoleOnStdErr(cfg.isShowConsoleOnStdErr)
        configuration.setShowConsoleOnStdOut(cfg.isShowConsoleOnStdOut)
        configuration.testConfigurationData = cfg.testConfigurationData.copy(configuration)
      }
      runIsPossible
    }

    if (sourceElement.isNull)
      false
    else if (!hasTestSuitesInModuleDependencies(context))
      false
    else
      createConfigurationFromLocation(context.getLocation) match {
        case Some((testElement, confSettings)) if testElement != null && confSettings != null =>
          setup(testElement, confSettings)
        case _ =>
          false
      }
  }

  def createConfigurationFromLocation(
    location: PsiElementLocation
  ): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    val element = location.getPsiElement
    if (element == null) return None
    createConfigurationForElement(location, element)
  }

  protected def createConfigurationForElement(
    location: PsiElementLocation,
    element: PsiElement
  ): Option[(PsiElement, RunnerAndConfigurationSettings)] =
    if (element.is[PsiPackage, PsiDirectory])
      getTestPackageWithPackageName(element) match {
        case (null, _) => None
        case (testPackage, packageName) =>
          createConfigurationForPackage(location, element, testPackage, packageName)
      }
    else
      getTestClassWithTestName(location) match {
        case (null, _) => None
        case (testClass, testName) =>
          createConfigurationForTestClass(location, testClass, testName)
      }

  private def getTestPackageWithPackageName(element: PsiElement): (PsiPackage, String) = element match {
    case dir: PsiDirectory => (AbstractJavaTestConfigurationProducer.checkPackage(dir), dir.name)
    case pack: PsiPackage  => (pack, pack.name)
    case _                 => (null, null)
  }

  private def createConfigurationForPackage(
    location: PsiElementLocation,
    element: PsiElement,
    testPackage: PsiPackage,
    packageName: String
  ): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    val displayName   = configurationNameForPackage(packageName)
    val settings      = RunManager.getInstance(location.getProject).createConfiguration(displayName, getConfigurationFactory)
    val configuration = settings.getConfiguration.asInstanceOf[T]

    prepareRunConfigurationForPackage(configuration, location, testPackage, displayName)

    extendCreatedConfiguration(location, configuration)
    Some((element, settings))
  }

  protected def configurationNameForPackage(packageName: String): String

  private def prepareRunConfigurationForPackage(configuration: T, location: PsiElementLocation, testPackage: PsiPackage, displayName: String): Unit = {
    if (configuration.getModule == null)
      prepareModule(configuration, location)

    configuration.setGeneratedName(displayName)

    val testDataOld = configuration.testConfigurationData
    val testDataNew = AllInPackageTestData(configuration, sanitize(testPackage.getQualifiedName))
    TestConfigurationData.copy(testDataOld, testDataNew)
    testDataNew.initWorkingDir()

    configuration.testConfigurationData = testDataNew
  }

  private def prepareModule(configuration: T, location: PsiElementLocation): Unit =
    for {
      module <- Option(location.getModule)
      jvmModule <- module.findJVMModule
    } configuration.setModule(jvmModule)

  private def createConfigurationForTestClass(
    location: PsiElementLocation,
    testClass: ScTypeDefinition,
    testName: String
  ): Option[(ScTypeDefinition, RunnerAndConfigurationSettings)] = {
    val displayName   = configurationName(testClass, testName)
    val settings      = RunManager.getInstance(location.getProject).createConfiguration(displayName, getConfigurationFactory)
    val configuration = settings.getConfiguration.asInstanceOf[T]

    prepareRunConfiguration(configuration, location, testClass, testName)

    extendCreatedConfiguration(location, configuration)
    Some((testClass, settings))
  }

  protected def configurationName(testClass: ScTypeDefinition, testName: String): String

  protected def prepareRunConfiguration(configuration: T, location: PsiElementLocation, testClass: ScTypeDefinition, testName: String): Unit = {
    if (configuration.getModule == null)
      prepareModule(configuration, location)

    val testDataOld = configuration.testConfigurationData
    val testDataNew = ClassTestData(configuration, sanitize(testClass.qualifiedName), testName)
    TestConfigurationData.copy(testDataOld, testDataNew)
    testDataNew.initWorkingDir()

    configuration.testConfigurationData = testDataNew
  }

  private def extendCreatedConfiguration(location: PsiElementLocation, configuration: T): Unit = {
    // hack with casting needed to avoid false-positive inference errors from Kotlin interop
    val instance = JavaRunConfigurationExtensionManager.getInstance.asInstanceOf[RunConfigurationExtensionsManager[RunConfigurationBase[_], _]]
    instance.extendCreatedConfiguration(configuration, location)
  }

  protected def isRunPossibleFor(configuration: T, testElement: PsiElement): Boolean = {
    // We check `hasTestSuitesInModuleDependencies` once again because configuration is only created when classpath
    // of the module from the context contains test suite class.
    // However the created configuration can contain another module, defined in the template.
    // For that case we do not want to hide  'Create Test Configuration' item in context menu.
    // Instead, we allow opening "create configuration" dialog and show the error there, in the bottom of the dialog.
    testElement match {
      case cl: PsiClass if hasTestSuitesInModuleDependencies(configuration) =>
        configuration.isValidSuite(cl) ||
          ClassInheritorsSearch.search(cl).asScala.exists(configuration.isValidSuite)
      case _ => true
    }
  }

  override def onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable): Unit = {
    val success = configuration.getConfiguration match {
      case config: AbstractTestRunConfiguration =>
        config.testConfigurationData match {
          case testData: ClassTestData if config.isValidSuite(testData.getClassPathClazz)=>
            onFirstRun(config, testData, context, startRunnable)
          case _ => false
        }
      case _ => false
    }
    if (!success)
      startRunnable.run()
  }

  private def onFirstRun(
    config: AbstractTestRunConfiguration,
    testData: ClassTestData,
    context: ConfigurationContext,
    startRunnable: Runnable,
  ): Boolean = {
    val testClass = testData.getClassPathClazz
    val inheritorsChooser = new MyInheritorChooser(config, testData)
    inheritorsChooser.runMethodInAbstractClass(context, startRunnable, null, testClass)
  }

  //TODO: implement me properly
  override final def isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean = {
    val runnerClassName = configuration.runnerClassName
    if (runnerClassName == null || runnerClassName != configuration.runnerClassName)
      return false

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
    val (testClass, testName) = getTestClassWithTestName(location)
    if (testClass == null) return false
    val testClassPath = testClass.qualifiedName
    configuration.testConfigurationData match {
      case testData: SingleTestData => testData.testClassPath == testClassPath && testData.testName == testName
      case classData: ClassTestData => classData.testClassPath == testClassPath && testName == null
      case _                        => false
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

  // do not display backticks in test class/package name
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")
}
