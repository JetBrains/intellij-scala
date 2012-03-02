package org.jetbrains.plugins.scala.runner

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.execution.junit.{JavaRuntimeConfigurationProducerBase, RuntimeConfigurationProducer}
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.execution.configurations.ConfigurationUtil
import com.intellij.openapi.project.Project
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.util.Comparing
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import com.intellij.psi._

/**
 * @author Alefas
 * @since 02.03.12
 */
class ScalaApplicationConfigurationProducer extends JavaRuntimeConfigurationProduceBaseAdapter(ApplicationConfigurationType.getInstance) with Cloneable {

  def getSourceElement: PsiElement = myPsiElement

  def createConfigurationByElement(_location: Location[_ <: PsiElement], context: ConfigurationContext): RunnerAndConfigurationSettings = {
    val location = JavaExecutionUtil.stepIntoSingleClass(_location)
    if (location == null) return null
    val element: PsiElement = location.getPsiElement
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile])return null
    if (!element.isPhysical) return null
    var currentElement: PsiElement = element
    var method: PsiMethod = findMain(currentElement)
    while (method != null) {
      val aClass: PsiClass = method.getContainingClass
      if (ConfigurationUtil.MAIN_CLASS.value(aClass)) {
        myPsiElement = method
        return createConfiguration(aClass, context, location)
      }
      currentElement = method.getParent
      method = findMain(currentElement)
    }
    val aClass: PsiClass = getMainClass(element)
    if (aClass == null) return null
    myPsiElement = aClass
    createConfiguration(aClass, context, location)
  }

  private def getMainClass(_element: PsiElement): PsiClass = {
    var element = _element
    while (element != null) {
      element match {
        case o: ScObject =>
          val aClass = o.fakeCompanionClassOrCompanionClass
          if (PsiMethodUtil.findMainInClass(aClass) != null) {
            return aClass
          }
        case file: ScalaFile =>
          val classes: Array[PsiClass] = file.getClasses
          for (aClass <- classes) {
            if (PsiMethodUtil.findMainInClass(aClass) != null) {
              return aClass
            }
          }
        case _ =>
      }
      element = element.getParent
    }
    null
  }

  private def createConfiguration(aClass: PsiClass, context: ConfigurationContext,
                                  location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val project: Project = aClass.getProject
    var settings: RunnerAndConfigurationSettings = cloneTemplateConfiguration(project, context)
    val configuration: ApplicationConfiguration = settings.getConfiguration.asInstanceOf[ApplicationConfiguration]
    configuration.MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(aClass)
    configuration.setName(configuration.getGeneratedName)
    setupConfigurationModule(context, configuration)
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
    settings
  }

  protected override def findExistingByElement(location: Location[_ <: PsiElement],
                                               existingConfigurations: Array[RunnerAndConfigurationSettings],
                                               context: ConfigurationContext): RunnerAndConfigurationSettings = {
    val aClass: PsiClass = getMainClass(location.getPsiElement)
    if (aClass == null) {
      return null
    }
    val predefinedModule: Module = ((RunManagerEx.getInstanceEx(location.getProject).asInstanceOf[RunManagerImpl]).
      getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[ApplicationConfiguration]).getConfigurationModule.getModule
    for (existingConfiguration <- existingConfigurations) {
      val appConfiguration: ApplicationConfiguration = existingConfiguration.getConfiguration.asInstanceOf[ApplicationConfiguration]
      if (Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), appConfiguration.MAIN_CLASS_NAME)) {
        if (Comparing.equal(location.getModule, appConfiguration.getConfigurationModule.getModule)) {
          return existingConfiguration
        }
        val configurationModule: Module = appConfiguration.getConfigurationModule.getModule
        if (Comparing.equal(location.getModule, configurationModule)) return existingConfiguration
        if (Comparing.equal(predefinedModule, configurationModule)) {
          return existingConfiguration
        }
      }
    }
    null
  }

  private var myPsiElement: PsiElement = null

  private def getContainingMethod(_element: PsiElement): PsiMethod = {
    var element = _element
    while (element != null) {
      if (element.isInstanceOf[PsiMethod]) return element.asInstanceOf[PsiMethod]
      else element = element.getParent
    }
    element.asInstanceOf[PsiMethod]
  }

  private def findMain(_element: PsiElement): PsiMethod = {
    var element = _element
    var method: PsiMethod = getContainingMethod(element)
    while (method != null) {
      def isMainMethod(method: PsiMethod): Option[PsiMethod] = {
        method match {
          case f: ScFunction =>
            f.getContainingClass match {
              case o: ScObject =>
                val wrapper = f.getFunctionWrapper(true, false)
                if (PsiMethodUtil.isMainMethod(wrapper)) Some(wrapper)
                else None
              case _ => None
            }
          case _ => None
        }
      }
      isMainMethod(method) match {
        case Some(method) => return method
        case _ => element = method.getParent
      }
      method = getContainingMethod(element)
    }
    null
  }
}

