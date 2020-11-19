package org.jetbrains.plugins.scala
package runner

import java.lang.Boolean.TRUE

import com.intellij.execution._
import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext, RunConfigurationProducer}
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationProducer, ApplicationConfigurationType}
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.{Key, Ref}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

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

abstract class BaseScalaApplicationConfigurationProducer[T <: ApplicationConfiguration](configurationType: ApplicationConfigurationType)
  extends JavaRuntimeConfigurationProduceBaseAdapter[T](configurationType)
    with Cloneable {

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

  private def hasScala3MainAncestorWithName(element: PsiElement, name: String): Boolean = {
    val candidates = ScalaMainMethodUtil.containingScala3MainMethodCandidates(element)
    val found = candidates.find(nameForScala3MainConfiguration(_) == name)
    found.isDefined
  }

  @Measure
  override def isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean = {
    val location = context.getLocation
    if (location == null) return false
    val element = context.getPsiLocation
    if (element == null) return false
    val file = element.getContainingFile
    if (!file.isInstanceOf[ScalaFile]) return false

    if (hasScala3MainAncestorWithName(element, configuration.getMainClassName)) {
      sameModule(configuration, location)
    }
    else if (hasClassAncestorWithName(element, configuration.getMainClassName)) {
      val containingObj = ScalaMainMethodUtil.findObjectWithMain(element)
      val sameName = containingObj.map(nameForConfiguration).contains(configuration.getMainClassName)
      sameName && sameModule(configuration, location) && {
        // We need to ensure that we are not in some scala 3 main method
        // This is required because we recognize created configuration even if context element is not a child
        // of object with a main method (see ScalaMainMethodUtil.findObjectWithMain.findTopLevel). And this can happen
        // when we right click on scala3 main method, but some configuration for object+main already exists
        val scala3Method = ScalaMainMethodUtil.findContainingScala3MainMethod(element)
        scala3Method.isEmpty
      }
    }
    else false
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

  @Measure
  override def setupConfigurationFromContext(configuration: T, context: ConfigurationContext, sourceElement: Ref[PsiElement]): Boolean = {
    val location = JavaExecutionUtil.stepIntoSingleClass(context.getLocation)
    if (location == null) return false
    val element: PsiElement = location.getPsiElement
    if (element == null) return false
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile]) return false
    if (!element.isPhysical) return false

    ScalaMainMethodUtil.findContainingScala3MainMethod(element) match {
      case Some(scala3Main) =>
        val mainClassName = nameForScala3MainConfiguration(scala3Main)
        setupConfiguration(mainClassName, scala3Main, context, location, configuration)
        sourceElement.set(scala3Main)
        true
      case _ =>
        ScalaMainMethodUtil.findMainClassAndSourceElem(element) match {
          case Some((clazz, elem)) =>
            val mainClassName = nameForConfiguration(clazz)
            setupConfiguration(mainClassName, clazz, context, location, configuration)
            sourceElement.set(elem)
            true
          case _ =>
            false
        }
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

  private def nameForConfiguration(c: PsiClass): String = c.qualifiedName

  // scala3 @main method always uses qualified name of the containing file even if the method is not top level (at least with version 3.0.0-M1)
  private def nameForScala3MainConfiguration(fun: ScFunctionDefinition): String = {
    val packageFqn = fun.topLevelQualifier.filter(_.nonEmpty)
    (packageFqn.toSeq :+ fun.name).mkString(".")
  }
}
