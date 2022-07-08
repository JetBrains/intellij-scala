package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.components.libextensions.DynamicExtensionPoint
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import scala.collection.immutable.ArraySeq
import scala.util.control.ControlThrowable

class SyntheticMembersInjector {
  /**
    * This method allows to add custom functions to any class, object or trait.
    * This includes synthetic companion object.
    *
    * Context for this method will be class. So inner types and imports of this class
    * will not be available. But you can use anything outside of it.
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

  private def implementations(implicit p: Project): Seq[SyntheticMembersInjector] =
    EP_NAME.getExtensions.toSeq ++ DYN_EP.getExtensions

  private val LOG: Logger = Logger.getInstance(getClass)


  def inject(source: ScTypeDefinition): Seq[ScFunction] = {
    if (!source.isValid) return Seq.empty

    implicit val ctx: Project = source.getProject
    val builder = ArraySeq.newBuilder[ScFunction]

    for {
      injector <- implementations
      template <- injector.injectFunctions(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      val function = ScalaPsiElementFactory.createMethodWithContext(template, context, source)
      if (function == null)
        throw new RuntimeException(s"Failed to parse method for class $source: '$template'")
      function.syntheticNavigationElement = context
      function.syntheticContainingClass = source
      builder += function
    } catch {
      case c: ControlFlowException => throw c
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    builder.result()
  }

  def injectInners(source: ScTypeDefinition): Seq[ScTypeDefinition] = {
    if (!source.isValid) return Seq.empty

    val builder = ArraySeq.newBuilder[ScTypeDefinition]
    implicit val ctx: Project = source.getProject
    for {
      injector <- implementations
      template <- injector.injectInners(source)
    } try {
      val contextClass = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      val context = templateBodyOrSynthetic(contextClass)
      val td = ScalaPsiElementFactory.createTypeDefinitionWithContext(template, context, source)
      td.syntheticContainingClass = source
      updateSynthetic(td, context)
      builder += td
    } catch {
      case c: ControlFlowException => throw c
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    builder.result()
  }

  def needsCompanion(source: ScTypeDefinition): Boolean = {
    if (!source.isValid && DumbService.getInstance(source.getProject).isDumb) return false

    implicit val ctx: Project = source.getProject
    (EP_NAME.getExtensions ++ DYN_EP.getExtensions).exists(_.needsCompanionObject(source))
  }

  def injectSupers(source: ScTypeDefinition): Seq[ScTypeElement] = {
    if (!source.isValid) return Seq.empty

    val builder = Seq.newBuilder[ScTypeElement]
    implicit val ctx: Project = source.getProject
    for {
      injector <- implementations
      supers <- injector.injectSupers(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      builder += ScalaPsiElementFactory.createTypeElementFromText(supers, context, source)
    } catch {
      case c: ControlFlowException => throw c
      case e: Throwable =>
        logError(s"Error during parsing type element from injector: ${injector.getClass.getName}", e)
    }
    builder.result()
  }

  def injectMembers(source: ScTypeDefinition): Seq[ScMember] = {
    if (!source.isValid) return Seq.empty

    val builder = ArraySeq.newBuilder[ScMember]
    implicit val ctx: Project = source.getProject
    for {
      injector <- implementations
      template <- injector.injectMembers(source)
    } try {
      val context = source match {
        //        case o: ScObject if o.isSyntheticObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(source)
        case _ => source
      }
      val member = ScalaPsiElementFactory.createDefinitionWithContext(template, context, source)
      member.context = context
      member.syntheticNavigationElement = context
      member.syntheticContainingClass = context

      updateSynthetic(member, context)
      if (!member.hasModifierProperty("override")) builder += member
    } catch {
      case c: ControlFlowException => throw c
      case e: Throwable =>
        logError(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    builder.result()
  }

  private def logError(message: String, t: Throwable): Unit = {
    t match {
      case e@(_: ControlFlowException | _: ControlThrowable) => throw e
      case _ => LOG.error(message, t)
    }
  }

  private def updateSynthetic(element: ScMember, context: PsiElement): Unit = element match {
    case td: ScTypeDefinition =>
      td.syntheticNavigationElement = context
      td.membersWithSynthetic.foreach(updateSynthetic(_, context))
    case fun: ScFunction => fun.syntheticNavigationElement = context
    case _ => //todo: ?
  }

  private def templateBodyOrSynthetic(td: ScTypeDefinition): ScTemplateBody = {
    val extendsBlock = td.extendsBlock

    @CachedInUserData(td, ModTracker.libraryAware(td))
    def syntheticTemplateBody: ScTemplateBody = {
      val body = ScalaPsiElementFactory.createTemplateBody(td.getProject)
      body.context = extendsBlock
      body.child = extendsBlock.getLastChild
      body
    }

    extendsBlock.templateBody.getOrElse(syntheticTemplateBody)
  }
}
