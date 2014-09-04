package org.jetbrains.plugins.scala.runner

import java.util

import com.intellij.execution._
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.configurations.ConfigurationUtil
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.{util => _, _}
import com.intellij.psi.util.PsiMethodUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, ScFunctionWrapper}

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
      val aClass: PsiClass = method.containingClass
      if (ConfigurationUtil.MAIN_CLASS.value(aClass)) {
        myPsiElement = method match {
          case fun: ScFunction => fun.getFirstChild
          case fun: ScFunctionWrapper => fun.function.getFirstChild
          case elem => elem.getFirstChild
        }
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

  /**
   * This is not for Java only. However it uses getClasses, to have possibility use [[com.intellij.psi.util.PsiMethodUtil.findMainInClass]]
   */
  private def getMainClass(_element: PsiElement): PsiClass = {
    var element = _element
    while (element != null) {
      element match {
        case clazz: PsiClassWrapper =>
          if (PsiMethodUtil.findMainInClass(clazz) != null) {
            return clazz
          }
        case o: ScObject =>
          val aClass = o.fakeCompanionClassOrCompanionClass
          if (PsiMethodUtil.findMainInClass(aClass) != null) {
            return aClass
          }
        case file: ScalaFile =>
          val classes: Array[PsiClass] = file.getClasses //this call is ok
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
    val settings: RunnerAndConfigurationSettings = cloneTemplateConfiguration(project, context)
    val configuration: ApplicationConfiguration = settings.getConfiguration.asInstanceOf[ApplicationConfiguration]
    configuration.MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(aClass)
    configuration.setName(configuration.suggestedName())
    setupConfigurationModule(context, configuration)
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
    settings
  }

  protected override def findExistingByElement(location: Location[_ <: PsiElement],
                                               existingConfigurations: util.List[RunnerAndConfigurationSettings],
                                               context: ConfigurationContext): RunnerAndConfigurationSettings = {
    val aClass: PsiClass = getMainClass(location.getPsiElement)
    if (aClass == null) {
      return null
    }
    val predefinedModule: Module = RunManagerEx.getInstanceEx(location.getProject).asInstanceOf[RunManagerImpl].
            getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[ApplicationConfiguration].getConfigurationModule.getModule
    import scala.collection.JavaConversions._
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
      element match {
        case method: PsiMethod => return method
        case _ => element = element.getParent
      }
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
            f.containingClass match {
              case o: ScObject =>
                for {
                  wrapper <- f.getFunctionWrappers(isStatic = true, isInterface = false).headOption
                  if PsiMethodUtil.isMainMethod(wrapper)
                } yield wrapper
              case _ => None
            }
          case _ => None
        }
      }
      isMainMethod(method) match {
        case Some(mainMethod) => return mainMethod
        case _ => element = method.getParent
      }
      method = getContainingMethod(element)
    }
    null
  }
}

