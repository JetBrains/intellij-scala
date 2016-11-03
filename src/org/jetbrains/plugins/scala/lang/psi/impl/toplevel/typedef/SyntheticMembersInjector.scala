package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.{DumbService, Project}
import org.jetbrains.plugins.scala.components.libinjection.LibraryInjectorLoader
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable.ArrayBuffer
import scala.util.control.ControlThrowable

/**
 * @author Mikhail.Mutcianko
 * @since  26.12.14
 */
class SyntheticMembersInjector {
  /**
    * This method allows to add custom functions to any class, object or trait.
    * This includes synthetic companion object.
    *
    * Context for this method will be class. So inner types and imports of this class
    * will not be available. But you can use anything outside of it.
    *
    * Injected method will not participate in class overriding hierarchy unless this method
    * is marked with override modifier. Use it carefully, only when this behaviour is intended.
    *
    * @param source class to inject functions
    * @return sequence of functions text
    */
  def injectFunctions(source: ScTypeDefinition): Seq[String] = Seq.empty

  /**
    * This method allows to add custom inner classes or objects to any class, object or trait.
    * This includes synthetic companion object.
    *
    * Context for this inner will be class. So inner types and imports of this class
    * will not be available. But you can use anything outside of it.
    *
    * @param source class to inject functions
    * @return sequence of inners text
    */
  def injectInners(source: ScTypeDefinition): Seq[String] = Seq.empty

  /**
    * Use this method to mark class or trait, that it requires companion object.
    * Note that object as source is not possible.
    *
    * @param source class or trait
    * @return if this source requires companion object
    */
  def needsCompanionObject(source: ScTypeDefinition): Boolean = false

  /**
    * Use this method to add supers to class, trait or object. This supers will be added to the end of source supers.
    * Be careful and avoid cycles as you can't use information about current supers of this class.
    *
    * @param source class, trait or object
    * @return sequence of strings, containing super types.
    */
  def injectSupers(source: ScTypeDefinition): Seq[String] = Seq.empty

  def injectMembers(source: ScTypeDefinition): Seq[String] = Seq.empty
}

object SyntheticMembersInjector {
  type Kind = Kind.Value
  object Kind extends Enumeration {
    val Class, Object, Trait = Value
  }

  private val CLASS_NAME = "org.intellij.scala.syntheticMemberInjector"

  val EP_NAME: ExtensionPointName[SyntheticMembersInjector] = ExtensionPointName.create(CLASS_NAME)

  private val LOG: Logger = Logger.getInstance(getClass)

  private val injectedExtensions = { proj: Project =>
    try {
      LibraryInjectorLoader.getInstance(proj).getInjectorInstances(classOf[SyntheticMembersInjector])
    } catch {
      case e: Throwable =>
        logError("Failed to get dynamic injector", e)
        Seq.empty
    }
  }

  def inject(source: ScTypeDefinition, withOverride: Boolean): Seq[ScFunction] = {
    val buffer = new ArrayBuffer[ScFunction]()
    for {
      injector <- EP_NAME.getExtensions.toSet ++ injectedExtensions(source.getProject).toSet
      template <- injector.injectFunctions(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      val function = ScalaPsiElementFactory.createMethodWithContext(template, context, source)
      function.setSynthetic(context)
      function.syntheticContainingClass = Some(source)
      if (withOverride ^ !function.hasModifierProperty("override")) buffer += function
    } catch {
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }

  def injectInners(source: ScTypeDefinition): Seq[ScTypeDefinition] = {
    val buffer = new ArrayBuffer[ScTypeDefinition]()
    for {
      injector <- EP_NAME.getExtensions.toSet ++ injectedExtensions(source.getProject).toSet
      template <- injector.injectInners(source)
    } try {
      val context = (source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }).extendsBlock
      val td = ScalaPsiElementFactory.createTypeDefinitionWithContext(template, context, source)
      td.syntheticContainingClass = Some(source)
      def updateSynthetic(element: ScMember): Unit = {
        element match {
          case td: ScTypeDefinition =>
            td.setSynthetic(context)
            td.members.foreach(updateSynthetic)
          case fun: ScFunction => fun.setSynthetic(context)
          case _ => //todo: ?
        }
      }
      updateSynthetic(td)
      buffer += td
    } catch {
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }

  def needsCompanion(source: ScTypeDefinition): Boolean = {
    if (DumbService.getInstance(source.getProject).isDumb) return false
    EP_NAME.getExtensions.exists(_.needsCompanionObject(source))
  }

  def injectSupers(source: ScTypeDefinition): Seq[ScTypeElement] = {
    val buffer = new ArrayBuffer[ScTypeElement]()
    for {
      injector <- EP_NAME.getExtensions
      supers <- injector.injectSupers(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      buffer += ScalaPsiElementFactory.createTypeElementFromText(supers, context, source)
    } catch {
      case e: Throwable =>
        logError(s"Error during parsing type element from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }

  private def logError(message: String, t: Throwable): Unit = {
    t match {
      case e @ (_: ControlFlowException | _: ControlThrowable) => throw e
      case _ => LOG.error(message, t)
    }
  }

  def injectMembers(source: ScTypeDefinition): Seq[ScMember] = {
    val buffer = new ArrayBuffer[ScMember]()
    for {
      injector <- EP_NAME.getExtensions.toSet ++ injectedExtensions(source.getProject).toSet
      template <- injector.injectMembers(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      val member = ScalaPsiElementFactory.createDefinitionWithContext(template, context, source)
      member.setSynthetic(context)
      member.syntheticContainingClass = Some(source)
      if (!member.hasModifierProperty("override")) buffer += member
    } catch {
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }
}
