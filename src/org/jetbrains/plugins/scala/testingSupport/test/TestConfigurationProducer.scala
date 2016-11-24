package org.jetbrains.plugins.scala
package testingSupport.test

import javax.swing.ListCellRenderer

import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext, RunConfigurationProducer}
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.Location
import com.intellij.execution.junit.InheritorChooser
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.{Condition, Ref}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi._
import com.intellij.ui.components.JBList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @author Roman.Shein
 *         Date: 11.12.13
 */
abstract class TestConfigurationProducer(configurationType: ConfigurationType) extends RunConfigurationProducer[AbstractTestRunConfiguration](configurationType) with AbstractTestConfigurationProducer{

  protected def isObjectInheritor(clazz: ScTypeDefinition, fqn: String): Boolean = {
    val suiteClazz = ScalaPsiManager.instance(clazz.getProject).getCachedClass(fqn, clazz.getResolveScope, ScalaPsiManager.ClassCategory.OBJECT)
    if (suiteClazz == null) return false
    ScalaPsiUtil.cachedDeepIsInheritor(clazz, suiteClazz)
  }

  def getLocationClassAndTest(location: Location[_ <: PsiElement]): (ScTypeDefinition, String)

  override def setupConfigurationFromContext(configuration: AbstractTestRunConfiguration, context: ConfigurationContext, sourceElement: Ref[PsiElement]): Boolean = {
    if (sourceElement.isNull) {
      false
    }
    else {
      createConfigurationByElement(context.getLocation, context) match {
        case Some((testElement, resConfig)) if testElement != null && resConfig != null &&
          runPossibleFor(configuration, testElement) =>
          sourceElement.set(testElement)
          val cfg = resConfig.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
          configuration.setTestClassPath(cfg.getTestClassPath)
          configuration.setGeneratedName(cfg.suggestedName)
          configuration.setJavaOptions(cfg.getJavaOptions)
          configuration.setTestArgs(cfg.getTestArgs)
          configuration.setTestPackagePath(cfg.getTestPackagePath)
          configuration.setWorkingDirectory(cfg.getWorkingDirectory)
          configuration.setTestName(cfg.getTestName)
          configuration.setSearchTest(cfg.getSearchTest)
          configuration.setShowProgressMessages(cfg.getShowProgressMessages)
          configuration.setFileOutputPath(cfg.getOutputFilePath)
          configuration.setModule(cfg.getModule)
          configuration.setName(cfg.getName)
          configuration.setNameChangedByUser(!cfg.isGeneratedName)
          configuration.setSaveOutputToFile(cfg.isSaveOutputToFile)
          configuration.setShowConsoleOnStdErr(cfg.isShowConsoleOnStdErr)
          configuration.setShowConsoleOnStdOut(cfg.isShowConsoleOnStdOut)
          configuration.setTestKind(cfg.getTestKind)
          true
        case _ =>
          false
      }
    }
  }

  override def onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable): Unit = {
    configuration.getConfiguration match {
      case config: AbstractTestRunConfiguration =>
        val testClass = config.getClassPathClazz
        if (!(config.isInvalidSuite(testClass) &&
          new InheritorChooser() {

            override def runMethodInAbstractClass(context: ConfigurationContext, performRunnable: Runnable,
                                                  psiMethod: PsiMethod, containingClass: PsiClass,
                                                  acceptAbstractCondition: Condition[PsiClass]): Boolean = {
              //TODO this is mostly copy-paste from InheritorChooser; get rid of this once we support pattern test runs
              if (containingClass == null) return false
              import scala.collection.JavaConversions._
              val classes = ClassInheritorsSearch.search(containingClass).filterNot{config.isInvalidSuite}.toList
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
              import scala.collection.JavaConversions._
              val classesSorted = classes.sorted(Ordering.comparatorToOrdering(renderer.getComparator))
              val jbList = new JBList(classesSorted:_*)
              //scala type system gets confused because someone forgot generics in PsiElementListCellRenderer definition
              jbList.setCellRenderer(renderer.asInstanceOf[ListCellRenderer[PsiClass]])
              JBPopupFactory.getInstance().createListPopupBuilder(jbList).setTitle("Choose executable classes to run " +
                (if (psiMethod != null) psiMethod.getName else containingClass.getName)).setMovable(false).
                setResizable(false).setRequestFocus(true).setItemChoosenCallback(new Runnable() {
                override def run(): Unit = {
                  val values = jbList.getSelectedValuesList
                  if (values == null) return
                  if (values.size == 1) {
                    runForClass(values.head, psiMethod, context, performRunnable)
                  }
                }
              }).createPopup().showInBestPositionFor(context.getDataContext)
              true
            }

//            override protected def runForClasses(classes: java.util.List[PsiClass], method: PsiMethod,
//                                                 context: ConfigurationContext, performRunnable: Runnable) {
//
//              performRunnable.run()
//            }

            override protected def runForClass(aClass: PsiClass, psiMethod: PsiMethod, context: ConfigurationContext,
                                               performRunnable: Runnable) {
              config.setTestClassPath(aClass.getQualifiedName)
              config.setName(StringUtil.getShortName(aClass.getQualifiedName) + (if (config.getTestName != "") "." + config.getTestName else ""))
              Option(ScalaPsiUtil.getModule(aClass)) foreach config.setModule
              performRunnable.run()
            }
          }.runMethodInAbstractClass(context, startRunnable, null, testClass))) super.onFirstRun(configuration, context, startRunnable)
      case _ => startRunnable.run()
    }
  }

  override def isConfigurationFromContext(configuration: AbstractTestRunConfiguration, context: ConfigurationContext): Boolean = {
    //TODO: implement me properly
    val runnerClassName = configuration.mainClass

    if (runnerClassName != null && runnerClassName == configuration.mainClass) {
      val configurationModule: Module = configuration.getConfigurationModule.getModule
      if (context.getLocation != null) {
        isConfigurationByLocation(configuration, context.getLocation)
      } else {
        (context.getModule == configurationModule ||
                context.getRunManager.getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
                        .getConfigurationModule.getModule == configurationModule) && configuration.getTestClassPath == null && configuration.getTestName == null
      }
    } else false
  }

  protected def runPossibleFor(configuration: AbstractTestRunConfiguration, testElement: PsiElement): Boolean = {
    import scala.collection.JavaConversions._
    testElement match {
      case cl: PsiClass => !configuration.isInvalidSuite(cl) || ClassInheritorsSearch.search(cl).iterator().exists(!configuration.isInvalidSuite(_))
      case _ => true
    }
  }
}
