package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.components.libextensions.DynamicExtensionPoint
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTypeDefinition}
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
  val DYN_EP: DynamicExtensionPoint[SyntheticMembersInjector] = new DynamicExtensionPoint[SyntheticMembersInjector]

  private val LOG: Logger = Logger.getInstance(getClass)


  def inject(source: ScTypeDefinition, withOverride: Boolean): Seq[ScFunction] = {
    implicit val ctx: Project = source.getProject
    val buffer = new ArrayBuffer[ScFunction]()
    for {
      injector <- EP_NAME.getExtensions.toSet ++ DYN_EP.getExtensions
      template <- injector.injectFunctions(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      val function = ScalaPsiElementFactory.createMethodWithContext(template, context, source)
      if (function == null)
        throw new RuntimeException(s"Failed to parse method for class $source: '$template'")
      function.setSynthetic(context)
      function.setSyntheticContainingClass(source)
      if (withOverride ^ !function.hasModifierProperty("override")) buffer += function
    } catch {
      case p: ProcessCanceledException => throw p
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }

  def updateSynthetic(element: ScMember, context: PsiElement): Unit = {
    element match {
      case td: ScTypeDefinition =>
        td.setSynthetic(context)
        td.members.foreach(updateSynthetic(_, context))
      case fun: ScFunction => fun.setSynthetic(context)
      case _ => //todo: ?
    }
  }

  def injectInners(source: ScTypeDefinition): Seq[ScTypeDefinition] = {
    val buffer = new ArrayBuffer[ScTypeDefinition]()
    implicit val ctx: Project = source.getProject
    for {
      injector <- EP_NAME.getExtensions.toSet ++ DYN_EP.getExtensions
      template <- injector.injectInners(source)
    } try {
      val context = (source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }).extendsBlock
      val td = ScalaPsiElementFactory.createTypeDefinitionWithContext(template, context, source)
      td.setSyntheticContainingClass(source)
      updateSynthetic(td, context)
      buffer += td
    } catch {
      case p: ProcessCanceledException => throw p
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }

  def needsCompanion(source: ScTypeDefinition): Boolean = {
    if (DumbService.getInstance(source.getProject).isDumb) return false
    implicit val ctx: Project = source.getProject
    (EP_NAME.getExtensions ++ DYN_EP.getExtensions).exists(_.needsCompanionObject(source))
  }

  def injectSupers(source: ScTypeDefinition): Seq[ScTypeElement] = {
    val buffer = new ArrayBuffer[ScTypeElement]()
    implicit val ctx: Project = source.getProject
    for {
      injector <- EP_NAME.getExtensions.toSet ++ DYN_EP.getExtensions
      supers <- injector.injectSupers(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      buffer += ScalaPsiElementFactory.createTypeElementFromText(supers, context, source)
    } catch {
      case p: ProcessCanceledException => throw p
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
    implicit val ctx: Project = source.getProject
    for {
      injector <- EP_NAME.getExtensions.toSet ++ DYN_EP.getExtensions
      template <- injector.injectMembers(source)
    } try {
      val context = source match {
//        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      val member = ScalaPsiElementFactory.createDefinitionWithContext(template, context, source)
      member.setContext(context, null)
      member.setSynthetic(context)
      member.setSyntheticContainingClass(context)
      context match {
        case c: ScClass if c.isCase && source != context => member.setSyntheticCaseClass(c)
        case _ =>
      }
      updateSynthetic(member, context)
      if (!member.hasModifierProperty("override")) buffer += member
    } catch {
      case p: ProcessCanceledException => throw p
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }
}
