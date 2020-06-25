package org.jetbrains.plugins.scala
package runner

import java.lang.Boolean.TRUE

import com.intellij.execution._
import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext}
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationProducer, ApplicationConfigurationType}
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.{Key, Ref}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

/**
 * @author Alefas
 * @since 02.03.12
 */
class ScalaApplicationConfigurationProducer
  extends BaseScalaApplicationConfigurationProducer[ApplicationConfiguration](ApplicationConfigurationType.getInstance)

private object ScalaApplicationConfigurationProducer {
  val key: Key[java.lang.Boolean] = Key.create("is.scala3.application.run.configuration")

  def isScala3ApplicationConfiguration(configuration: ApplicationConfiguration): Boolean =
    configuration.getCopyableUserData(key) == TRUE
}

abstract class BaseScalaApplicationConfigurationProducer[T <: ApplicationConfiguration](configurationType: ApplicationConfigurationType)
  extends JavaRuntimeConfigurationProduceBaseAdapter[T](configurationType)
    with Cloneable {

  private def createConfiguration(obj: PsiClass, context: ConfigurationContext,
                                  location: Location[_ <: PsiElement], configuration: T): Unit = {
    configuration.setMainClassName(nameForConfiguration(obj))
    configuration.setName(configuration.suggestedName())
    setupConfigurationModule(context, configuration)

    if (obj.isInScala3Module) {
      configuration.putCopyableUserData(ScalaApplicationConfigurationProducer.key, TRUE)
    }

    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
  }

  override def findModule(configuration: T, contextModule: Module): Module = {
    Option(super.findModule(configuration, contextModule))
      .flatMap(_.findJVMModule)
      .orNull
  }

  private def hasClassAncestorWithName(element: PsiElement, name: String): Boolean = {
    def classWithSameName(e: PsiElement) = e match {
      case c: PsiClass => nameForConfiguration(c) == name
      case _ => false
    }
    def fileHasClassWithSameName = element.getContainingFile match {
      case sf: ScalaFile => sf.typeDefinitions.exists(_.qualifiedName == name)
      case _ => false
    }

    element.withParentsInFile.exists(classWithSameName) || fileHasClassWithSameName
  }

  override def isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean = {
    val location = context.getLocation
    if (location == null) return false
    //use fast psi location check to filter off obvious candidates
    if (context.getPsiLocation == null || !hasClassAncestorWithName(context.getPsiLocation, configuration.getMainClassName)) return false
    val containingObj = ScalaMainMethodUtil.findObjectWithMain(context.getPsiLocation)
    val predefinedModule: Module = RunManagerEx.getInstanceEx(location.getProject).asInstanceOf[RunManagerImpl].
      getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[T].getConfigurationModule.getModule

    val module = configuration.getConfigurationModule.getModule
    val sameName = containingObj.map(nameForConfiguration).contains(configuration.getMainClassName)

    sameName && (location.getModule == module || predefinedModule == module)
  }

  override def setupConfigurationFromContext(configuration: T, context: ConfigurationContext, sourceElement: Ref[PsiElement]): Boolean = {
    val location = JavaExecutionUtil.stepIntoSingleClass(context.getLocation)
    if (location == null) return false
    val element: PsiElement = location.getPsiElement
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile]) return false
    if (!element.isPhysical) return false

    ScalaMainMethodUtil.findMainClassAndSourceElem(element) match {
      case Some((clazz, elem)) =>
        createConfiguration(clazz, context, location, configuration)
        sourceElement.set(elem)
        true
      case _ =>
        false
    }
  }

  override def shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean = {
    other.isProducedBy(classOf[ApplicationConfigurationProducer])
  }

  private def nameForConfiguration(c: PsiClass): String = c.qualifiedName
}
