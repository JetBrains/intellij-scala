package org.jetbrains.plugins.scala
package runner

import com.intellij.execution._
import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext, RunConfigurationProducer}
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationProducer, ApplicationConfigurationType}
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.junit.JavaRunConfigurationProducerBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.{Key, Ref}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt}

import java.lang.Boolean.TRUE

/**
 * @see java analog [[com.intellij.execution.application.ApplicationConfigurationProducer]]
 */
class ScalaApplicationConfigurationProducer
  extends BaseScalaApplicationConfigurationProducer[ApplicationConfiguration](ApplicationConfigurationType.getInstance)

object ScalaApplicationConfigurationProducer {
  val key: Key[java.lang.Boolean] = Key.create("is.scala3.application.run.configuration")

  def apply(): ScalaApplicationConfigurationProducer =
    RunConfigurationProducer.EP_NAME.findExtensionOrFail(classOf[ScalaApplicationConfigurationProducer])

  private[runner] def isScala3ApplicationConfiguration(configuration: ApplicationConfiguration): Boolean =
    configuration.getCopyableUserData(key) == TRUE
}

@scala.annotation.nowarn("msg=constructor JavaRunConfigurationProducerBase in class JavaRunConfigurationProducerBase is deprecated")
abstract class BaseScalaApplicationConfigurationProducer[T <: ApplicationConfiguration](configurationType: ApplicationConfigurationType)
  extends JavaRunConfigurationProducerBase[T](configurationType)
    with Cloneable {

  override def findModule(configuration: T, contextModule: Module): Module =
    Option(super.findModule(configuration, contextModule))
      .flatMap(_.findJVMModule)
      .orNull

  override def isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean = {
    val location = context.getLocation
    if (location == null) return false
    val element = context.getPsiLocation
    if (element == null) return false
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile]) return false

    val mainMethod = MyScalaMainMethodUtil.findMainMethodFromContext(element)
    mainMethod match {
      case Some(value) =>
        val mainClassName = mainClassNameFor(value)
        val namesEqual = mainClassName == configuration.getMainClassName
        namesEqual && sameModule(configuration, location)
      case _ =>
        false
    }
  }

  private def sameModule(configuration: T, location: Location[_ <: PsiElement]): Boolean = {
    val module = configuration.getConfigurationModule.getModule
    val predefinedModule = getPredefinedModule(location)
    location.getModule == module || predefinedModule == module
  }

  private def getPredefinedModule(location: Location[_ <: PsiElement]): Module = {
    val manager = RunManagerEx.getInstanceEx(location.getProject).asInstanceOf[RunManagerImpl]
    val template = manager.getConfigurationTemplate(getConfigurationFactory)
    template.getConfiguration.asInstanceOf[T].getConfigurationModule.getModule
  }

  override def setupConfigurationFromContext(
    configuration: T,
    context: ConfigurationContext,
    sourceElementRef: Ref[PsiElement]
  ): Boolean = {
    val location = context.getLocation
    if (location == null) return false
    val element: PsiElement = location.getPsiElement
    if (element == null) return false
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile]) return false
    if (!element.isPhysical) return false

    val mainMethodInfo = MyScalaMainMethodUtil.findMainMethodFromContext(element)
    mainMethodInfo match {
      case Some(value) =>
        val mainClassName = mainClassNameFor(value)
        val mainElement = value match {
          case MainMethodInfo.Scala3Style(method)    => method
          case MainMethodInfo.Scala2Style(_, obj, _) => obj
          case MainMethodInfo.WithCustomLauncher(clazz) => clazz
        }
        setupConfiguration(mainClassName, mainElement, context, location, configuration)
        val sourceElement = value.sourceElement
        sourceElementRef.set(sourceElement)
        true
      case None =>
        false
    }
  }

  private def setupConfiguration(
    mainClassName: String,
    element: PsiElement,
    context: ConfigurationContext,
    location: Location[_ <: PsiElement],
    configuration: T
  ): Unit = {
    configuration.setMainClassName(mainClassName)
    configuration.setName(configuration.suggestedName())
    setupConfigurationModule(context, configuration)

    if (element.isInScala3Module) {
      configuration.putCopyableUserData(ScalaApplicationConfigurationProducer.key, TRUE)
    }

    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
  }

  override def shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean = {
    other.isProducedBy(classOf[ApplicationConfigurationProducer])
  }

  private def mainClassNameFor(mainMethod: MainMethodInfo): String =
    mainMethod match {
      case MainMethodInfo.Scala3Style(method)       => mainClassNameForScala3AnnotatedMethod(method)
      case MainMethodInfo.Scala2Style(_, obj, _)    => mainClassNameForScala2(obj)
      case MainMethodInfo.WithCustomLauncher(clazz) => mainClassNameForScala2(clazz)
    }

  private def mainClassNameForScala2(c: PsiClass): String = c.qualifiedName

  /** @note scala3 @main method always uses qualified name of the containing file even if the method is not top level  */
  private def mainClassNameForScala3AnnotatedMethod(fun: ScFunctionDefinition): String = {
    val packageFqn = Option(ScalaPsiUtil.getPlacePackageName(fun)).filter(_.nonEmpty)
    (packageFqn.toSeq :+ fun.name).mkString(".")
  }
}
