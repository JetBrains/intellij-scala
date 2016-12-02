package org.jetbrains.plugins.scala.runner

import com.intellij.execution._
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.{util => _, _}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.CachedInsidePsiElement

/**
 * @author Alefas
 * @since 02.03.12
 */
class ScalaApplicationConfigurationProducer
  extends BaseScalaApplicationConfigurationProducer[ApplicationConfiguration](ApplicationConfigurationType.getInstance)

abstract class BaseScalaApplicationConfigurationProducer[T <: ApplicationConfiguration](configurationType: ApplicationConfigurationType)
  extends JavaRuntimeConfigurationProduceBaseAdapter[T](configurationType)
    with Cloneable {

  private def createConfiguration(obj: ScObject, context: ConfigurationContext,
                                  location: Location[_ <: PsiElement], configuration: T): Unit = {
    configuration.setMainClassName(nameForConfiguration(obj))
    configuration.setName(configuration.suggestedName())
    setupConfigurationModule(context, configuration)
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
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
    if (context.getPsiLocation == null || !hasClassAncestorWithName(context.getPsiLocation, configuration.MAIN_CLASS_NAME)) return false
    val containingObj = ScalaMainMethodUtil.findObjectWithMain(context.getPsiLocation)
    val predefinedModule: Module = RunManagerEx.getInstanceEx(location.getProject).asInstanceOf[RunManagerImpl].
      getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[T].getConfigurationModule.getModule

    val module = configuration.getConfigurationModule.getModule
    val sameName = containingObj.map(nameForConfiguration).contains(configuration.MAIN_CLASS_NAME)

    sameName && (location.getModule == module || predefinedModule == module)
  }

  override def setupConfigurationFromContext(configuration: T, context: ConfigurationContext, sourceElement: Ref[PsiElement]): Boolean = {
    val location = JavaExecutionUtil.stepIntoSingleClass(context.getLocation)
    if (location == null) return false
    val element: PsiElement = location.getPsiElement
    val containingFile = element.getContainingFile
    if (!containingFile.isInstanceOf[ScalaFile])return false
    if (!element.isPhysical) return false

    ScalaMainMethodUtil.findContainingMainMethod(element) match {
      case Some(funDef) =>
        val place = funDef.getFirstChild
        createConfiguration(funDef.containingClass.asInstanceOf[ScObject], context, location, configuration)
        sourceElement.set(place)
        true
      case _ =>
        ScalaMainMethodUtil.findObjectWithMain(element) match {
          case Some(obj) =>
            createConfiguration(obj, context, location, configuration)
            sourceElement.set(obj.fakeCompanionClassOrCompanionClass)
            true
          case None => false
        }
    }
  }

  private def nameForConfiguration(c: PsiClass): String = c.qualifiedName
}

object ScalaMainMethodUtil {

  def findContainingMainMethod(elem: PsiElement): Option[ScFunctionDefinition] = {
    elem.withParentsInFile.collectFirst {
      case funDef: ScFunctionDefinition if isMainMethod(funDef) => funDef
    }
  }

  def findObjectWithMain(element: PsiElement): Option[ScObject] = {
    def findTopLevel: Option[ScObject] = element.containingScalaFile.flatMap { file =>
      file.typeDefinitions.collectFirst {
        case o: ScObject if hasMainMethod(o) => o
      }
    }

    stableObject(element).filter(hasMainMethod) orElse findTopLevel
  }

  def hasMainMethod(obj: ScObject): Boolean = findMainMethod(obj).isDefined

  def findMainMethod(obj: ScObject): Option[PsiMethod] = {

    def declaredMain(obj: ScObject): Option[ScFunctionDefinition] = {
      obj.functions.collectFirst {
        case funDef: ScFunctionDefinition if isMainMethod(funDef) => funDef
      }
    }

    @CachedInsidePsiElement(obj, CachesUtil.enclosingModificationOwner(obj))
    def findMainMethodInner(): Option[PsiMethod] = {
      declaredMain(obj) orElse Option(PsiMethodUtil.findMainMethod(obj.fakeCompanionClassOrCompanionClass))
    }

    if (!ScalaPsiUtil.hasStablePath(obj)) None
    else findMainMethodInner()
  }

  private def isMainMethod(funDef: ScFunctionDefinition): Boolean = {
    def isInStableObject = stableObject(funDef).contains(funDef.containingClass)

    def hasJavaMainWrapper =
      funDef.getFunctionWrappers(isStatic = true, isInterface = false)
        .headOption
        .exists(PsiMethodUtil.isMainMethod)

    ScalaNamesUtil.toJavaName(funDef.name) == "main" && isInStableObject && hasJavaMainWrapper
  }

  private def stableObject(element: PsiElement): Option[ScObject] = element.withParentsInFile.collectFirst {
    case obj: ScObject if ScalaPsiUtil.hasStablePath(obj) => obj
  }
}