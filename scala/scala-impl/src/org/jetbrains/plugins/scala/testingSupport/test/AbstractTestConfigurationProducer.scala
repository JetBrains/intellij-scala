package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext, LazyRunConfigurationProducer}
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, ConfigurationTypeUtil, RunConfiguration}
import com.intellij.execution.junit.{InheritorChooser, JavaRuntimeConfigurationProducerBase}
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Condition, Ref}
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.components.JBList
import javax.swing.ListCellRenderer
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationType
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, SingleTestData, TestConfigurationData}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

abstract class AbstractTestConfigurationProducer[T <: AbstractTestRunConfiguration]
  extends LazyRunConfigurationProducer[T] {

  override final def getConfigurationFactory: ConfigurationFactory = configurationFactory

  def configurationFactory: ConfigurationFactory

  protected def suitePaths: Seq[String]

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean

  final def createConfigurationByLocation(location: Location[_ <: PsiElement]): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    val element = location.getPsiElement
    if (element == null) return None

    if (element.is[PsiPackage, PsiDirectory]) {
      getTestPackageWithPackageName(element) match {
        case (null, _) => None
        case (testPackage, packageName) =>
          createConfigurationForPackage(location, element, testPackage, packageName)
      }
    } else {
      getTestClassWithTestName(location) match {
        case (null, _) => None
        case (testClass, testName) =>
          createConfigurationForTestClass(location, testClass, testName)
      }
    }
  }

  private def createConfigurationForPackage(location: Location[_ <: PsiElement], element: PsiElement,
                                            testPackage: PsiPackage, packageName: String): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    val displayName   = configurationNameForPackage(packageName)
    val settings      = RunManager.getInstance(location.getProject).createConfiguration(displayName, getConfigurationFactory)
    val configuration = settings.getConfiguration.asInstanceOf[T]

    prepareRunConfigurationForPackage(configuration, location, testPackage, displayName)

    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
    Some((element, settings))
  }

  private def createConfigurationForTestClass(location: Location[_ <: PsiElement],
                                              testClass: ScTypeDefinition, testName: String) = {
    val displayName   = configurationName(testClass, testName)
    val settings      = RunManager.getInstance(location.getProject).createConfiguration(displayName, getConfigurationFactory)
    val configuration = settings.getConfiguration.asInstanceOf[T]

    prepareRunConfiguration(configuration, location, testClass, testName)

    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
    Some((testClass, settings))
  }

  protected def configurationNameForPackage(packageName: String): String

  protected def configurationName(testClass: ScTypeDefinition, testName: String): String

  // TODO: probably it should be moved to TestConfigurationData, reconsider after refactoring TestConfigurationData
  private def copyTestData(from: TestConfigurationData, to: TestConfigurationData): Unit = {
    to.javaOptions = from.javaOptions
    to.envs = from.envs
    to.testArgs = from.testArgs
    to.useSbt = from.useSbt
    to.useUiWithSbt = from.useUiWithSbt
    to.jrePath = from.jrePath
    to.showProgressMessages = from.showProgressMessages
    to.setWorkingDirectory(from.getWorkingDirectory)
  }

  private def prepareRunConfigurationForPackage(configuration: T, location: Location[_ <: PsiElement], testPackage: PsiPackage, displayName: String): Unit = {
    if (configuration.getModule == null) {
      prepareModule(configuration, location)
    }
    configuration.setGeneratedName(displayName)

    val testDataOld = configuration.testConfigurationData
    val testDataNew = AllInPackageTestData(configuration, sanitize(testPackage.getQualifiedName))
    copyTestData(testDataOld, testDataNew)
    testDataNew.initWorkingDir()

    configuration.testConfigurationData = testDataNew
  }

  protected def prepareRunConfiguration(configuration: T, location: Location[_ <: PsiElement], testClass: ScTypeDefinition, testName: String): Unit = {
    if (configuration.getModule == null) {
      prepareModule(configuration, location)
    }

    val testDataOld = configuration.testConfigurationData
    val testDataNew = ClassTestData(configuration, sanitize(testClass.qualifiedName), testName)
    copyTestData(testDataOld, testDataNew)
    testDataNew.initWorkingDir()

    configuration.testConfigurationData = testDataNew
  }

  private def prepareModule(configuration: T, location: Location[_ <: PsiElement]): Unit =
    for {
      module <- Option(location.getModule)
      jvmModule <- module.findJVMModule
    } configuration.setModule(jvmModule)

  // do not display backticks in test class/package name
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")

  private final def createConfigurationByElement(location: Location[_ <: PsiElement],
                                                 context: ConfigurationContext): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    context.getModule match {
      case module: Module if hasTestSuitesInModuleDependencies(module) =>
        val element = location.getPsiElement
        if (element == null) return None
        createConfigurationByLocation(location)//.asInstanceOf[RunnerAndConfigurationSettingsImpl]
      case _ =>
        null
    }
  }

  private def hasTestSuitesInModuleDependencies(module: Module): Boolean = {
    val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
    val psiManager = ScalaPsiManager.instance(module.getProject)
    suitePaths.exists(psiManager.getCachedClass(scope, _).isDefined)
  }

  protected def isObjectInheritor(clazz: ScTypeDefinition, fqn: String): Boolean =
    clazz.elementScope.getCachedObject(fqn)
      .exists {
        ScalaPsiUtil.isInheritorDeep(clazz, _)
      }

  private def getTestPackageWithPackageName(element: PsiElement): (PsiPackage, String) = element match {
    case dir: PsiDirectory => (JavaRuntimeConfigurationProducerBase.checkPackage(dir), dir.getName)
    case pack: PsiPackage  => (pack, pack.getName)
    case _                 => (null, null)
  }

  def getTestClassWithTestName(location: Location[_ <: PsiElement]): (ScTypeDefinition, String)

  override def setupConfigurationFromContext(configuration: T,
                                             context: ConfigurationContext,
                                             sourceElement: Ref[PsiElement]): Boolean = {
    def setup(testElement: PsiElement, confSettings: RunnerAndConfigurationSettings) = {
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
        configuration.testKind = cfg.testConfigurationData.getKind
        configuration.testConfigurationData = cfg.testConfigurationData.copy(configuration)
      }
      runIsPossible
    }

    if (sourceElement.isNull) {
      false
    } else {
      createConfigurationByElement(context.getLocation, context) match {
        case Some((testElement, confSettings)) if testElement != null && confSettings != null =>
          setup(testElement, confSettings)
        case _ =>
          false
      }
    }
  }

  override def onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable): Unit = {
    configuration.getConfiguration match {
      case config: AbstractTestRunConfiguration =>
        config.testConfigurationData match {
          case testData: ClassTestData =>
            val testClass = testData.getClassPathClazz
            if (!(config.isInvalidSuite(testClass) &&
              new InheritorChooser() {
                override def runMethodInAbstractClass(context: ConfigurationContext, performRunnable: Runnable,
                                                      psiMethod: PsiMethod, containingClass: PsiClass,
                                                      acceptAbstractCondition: Condition[PsiClass]): Boolean = {
                  //TODO this is mostly copy-paste from InheritorChooser; get rid of this once we support pattern test runs
                  if (containingClass == null) return false
                  val classes = ClassInheritorsSearch
                    .search(containingClass)
                    .asScala
                    .filterNot{config.isInvalidSuite}
                    .toList

                  if (classes.isEmpty) return false

                  if (classes.size == 1) {
                    runForClass(classes.head, psiMethod, context, performRunnable)
                    return true
                  }

                  val fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context.getDataContext)
                  fileEditor match {
                    case editor: TextEditor =>
                      val document = editor.getEditor.getDocument
                      val containingFile = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
                      containingFile match {
                        case owner: PsiClassOwner =>
                          val psiClasses = owner.getClasses
                          psiClasses.filter(classes.contains(_))
                          if (psiClasses.size == 1) {
                            runForClass(psiClasses.head, psiMethod, context, performRunnable)
                            return true
                          }
                        case _ =>
                      }
                    case _ =>
                  }
                  val renderer = new PsiClassListCellRenderer()
                  val classesSorted = classes.sorted(Ordering.comparatorToOrdering(renderer.getComparator))
                  val jbList = new JBList(classesSorted:_*)
                  //scala type system gets confused because someone forgot generics in PsiElementListCellRenderer definition
                  jbList.setCellRenderer(renderer.asInstanceOf[ListCellRenderer[PsiClass]])
                  val value = JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                  val testName = if (psiMethod != null) psiMethod.getName else containingClass.getName
                  value.setTitle(ScalaBundle.message("test.config.choose.executable.classes.to.run.test", testName)).setMovable(false).
                    setResizable(false).setRequestFocus(true).setItemChoosenCallback(new Runnable() {
                    override def run(): Unit = {
                      val values = jbList.getSelectedValuesList
                      if (values == null) return
                      if (values.size == 1) {
                        runForClass(values.get(0), psiMethod, context, performRunnable)
                      }
                    }
                  }).createPopup().showInBestPositionFor(context.getDataContext)
                  true
                }

                override protected def runForClass(aClass: PsiClass, psiMethod: PsiMethod, context: ConfigurationContext,
                                                   performRunnable: Runnable): Unit = {
                  testData.setTestClassPath(aClass.getQualifiedName)
                  config.setName(StringUtil.getShortName(aClass.getQualifiedName) + (testData match {
                    case single: SingleTestData => "." + single.getTestName
                    case _ => ""
                  }))
                  Option(ScalaPsiUtil.getModule(aClass)) foreach config.setModule
                  performRunnable.run()
                }
              }.runMethodInAbstractClass(context, startRunnable, null, testClass))) super.onFirstRun(configuration, context, startRunnable)
          case _ => startRunnable.run()
        }
      case _ => startRunnable.run()
    }
  }

  override def isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean = {
    //TODO: implement me properly
    val runnerClassName = configuration.runnerClassName

    if (runnerClassName != null && runnerClassName == configuration.runnerClassName) {
      val configurationModule: Module = configuration.getConfigurationModule.getModule
      if (context.getLocation != null) {
        isConfigurationByLocation(configuration, context.getLocation)
      } else {
        (context.getModule == configurationModule ||
                context.getRunManager.getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[T]
                        .getConfigurationModule.getModule == configurationModule) && !configuration.testConfigurationData.isInstanceOf[ClassTestData]
      }
    } else false
  }

  protected def isRunPossibleFor(configuration: T, testElement: PsiElement): Boolean = {
    val module = configuration.getModule
    // We check `hasTestSuitesInModuleDependencies` once again because configuration is only created when classpath
    // of the module from the context contains test suite class.
    // However the created configuration can contain another module, defined in the template.
    // For that case we do not want to hide  'Create Test Configuration' item in context menu.
    // Instead, we allow opening "create configuration" dialog and show the error there, in the bottom of the dialog.
    testElement match {
      case cl: PsiClass if module != null && hasTestSuitesInModuleDependencies(module) =>
        configuration.isValidSuite(cl) ||
          ClassInheritorsSearch
            .search(cl)
            .asScala
            .exists(configuration.isValidSuite)
      case _ => true
    }
  }
}
