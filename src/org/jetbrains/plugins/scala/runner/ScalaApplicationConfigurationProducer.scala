package org.jetbrains.plugins.scala.runner

import com.intellij.execution._
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.configurations.ConfigurationUtil
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.{util => _, _}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, ScFunctionWrapper}

/**
 * @author Alefas
 * @since 02.03.12
 */
class ScalaApplicationConfigurationProducer
  extends BaseScalaApplicationConfigurationProducer[ApplicationConfiguration](ApplicationConfigurationType.getInstance)

abstract class BaseScalaApplicationConfigurationProducer[T <: ApplicationConfiguration](configurationType: ApplicationConfigurationType)
  extends JavaRuntimeConfigurationProduceBaseAdapter[T](configurationType)
    with Cloneable {
  import ScalaApplicationConfigurationProducer._

  def createConfigurationByElement(_location: Location[_ <: PsiElement], context: ConfigurationContext, configuration: T): Boolean = {
    val location = JavaExecutionUtil.stepIntoSingleClass(_location)
    if (location == null) return false
    val element: PsiElement = location.getPsiElement
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile])return false
    if (!element.isPhysical) return false
    var currentElement: PsiElement = element
    var method: PsiMethod = findMain(currentElement)
    while (method != null) {
      val aClass: PsiClass = method.containingClass
      if (ConfigurationUtil.MAIN_CLASS.value(aClass)) {
        createConfiguration(aClass, context, location, configuration)
        return true
      }
      currentElement = method.getParent
      method = findMain(currentElement)
    }
    val aClass: PsiClass = getMainClass(element)
    if (aClass == null) return false
    createConfiguration(aClass, context, location, configuration)
    true
  }

  private def createConfiguration(aClass: PsiClass, context: ConfigurationContext,
                                  location: Location[_ <: PsiElement], configuration: T): Unit = {
    configuration.MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(aClass)
    configuration.setName(configuration.suggestedName())
    setupConfigurationModule(context, configuration)
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
  }

  private def hasClassAncestorWithName(_element: PsiElement, name: String): Boolean = {
    def isConfigClassWithName(clazz: PsiClass) = clazz match {
      case clazz: PsiClassWrapper if clazz.getQualifiedName == name => true
      case o: ScObject if o.fakeCompanionClassOrCompanionClass.getQualifiedName == name => true
      case _ => false
    }

    var element = _element
    do {
      element match {
        case clazz: PsiClass if isConfigClassWithName(clazz) => return true
        case f: ScalaFile if f.getClasses.exists(isConfigClassWithName) => return true
        case _ => element = element.getParent
      }
    } while (element != null)
    false
  }

  override def isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean = {
    val location = context.getLocation
    if (location == null) return false
    //use fast psi location check to filter off obvious candidates
    if (context.getPsiLocation == null || !hasClassAncestorWithName(context.getPsiLocation, configuration.MAIN_CLASS_NAME)) return false
    val aClass: PsiClass = getMainClass(context.getPsiLocation)
    if (aClass == null) return false
    val predefinedModule: Module = RunManagerEx.getInstanceEx(location.getProject).asInstanceOf[RunManagerImpl].
      getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[T].getConfigurationModule.getModule
    JavaExecutionUtil.getRuntimeQualifiedName(aClass) == configuration.MAIN_CLASS_NAME &&
      (location.getModule == configuration.getConfigurationModule.getModule || predefinedModule == configuration.getConfigurationModule.getModule)
  }

  override def setupConfigurationFromContext(configuration: T, context: ConfigurationContext, sourceElement: Ref[PsiElement]): Boolean = {
    val location = JavaExecutionUtil.stepIntoSingleClass(context.getLocation)
    if (location == null) return false
    val element: PsiElement = location.getPsiElement
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile])return false
    if (!element.isPhysical) return false
    var currentElement: PsiElement = element
    var method: PsiMethod = findMain(currentElement)
    while (method != null) {
      val aClass: PsiClass = method.containingClass
      if (ConfigurationUtil.MAIN_CLASS.value(aClass)) {
        val place = method match {
          case fun: ScFunction => fun.getFirstChild
          case fun: ScFunctionWrapper => fun.function.getFirstChild
          case elem => elem.getFirstChild
        }
        createConfiguration(aClass, context, location, configuration)
        sourceElement.set(place)
        return true
      }
      currentElement = method.getParent
      method = findMain(currentElement)
    }
    val aClass: PsiClass = getMainClass(element)
    if (aClass == null) return false
    createConfiguration(aClass, context, location, configuration)
    sourceElement.set(aClass)
    true
  }
}

object ScalaApplicationConfigurationProducer {

  /**
   * This is not for Java only. However it uses getClasses, to have possibility use [[com.intellij.psi.util.PsiMethodUtil.findMainInClass]]
   */
  def getMainClass(_element: PsiElement, firstTemplateDefOnly: Boolean = false): PsiClass = {
    var element = _element
    while (element != null) {
      element match {
        case clazz: PsiClassWrapper =>
          if (PsiMethodUtil.findMainInClass(clazz) != null) return clazz else if (firstTemplateDefOnly) return null
        case o: ScObject =>
          val aClass = o.fakeCompanionClassOrCompanionClass
          if (PsiMethodUtil.findMainInClass(aClass) != null) return aClass else if (firstTemplateDefOnly) return null
        case file: ScalaFile if !firstTemplateDefOnly =>
          val classes: Array[PsiClass] = file.getClasses //this call is ok
          for (aClass <- classes) {
            if (PsiMethodUtil.findMainInClass(aClass) != null) return aClass
          }
        case _ =>
      }
      element = element.getParent
    }
    null
  }
  
  def findMain(_element: PsiElement, firstContMethodOnly: Boolean = false): PsiMethod = {
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
        case _ => if (firstContMethodOnly) return null else element = method.getParent
      }
      method = getContainingMethod(element)
    }
    null
  }

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
}

