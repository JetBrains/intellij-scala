package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable.ArrayBuffer

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
   * will not be available. But you can use anything outside of
   * @param source class to inject functions
   * @return sequence of functions text
   */
  def injectFunctions(source: ScTypeDefinition): Seq[String] = Seq.empty

  /**
   * Use this method to mark class or trait, that it requires companion object.
   * Note that object as source is not possible.
   * @param source class or trait
   * @return if this source requires companion object
   */
  def needsCompanionObject(source: ScTypeDefinition): Boolean = false
}

object SyntheticMembersInjector {
  val LOG = Logger.getInstance(getClass)

  val EP_NAME: ExtensionPointName[SyntheticMembersInjector] =
    ExtensionPointName.create("org.intellij.scala.syntheticMemberInjector")

  def inject(source: ScTypeDefinition): Seq[ScFunction] = {
    val buffer = new ArrayBuffer[ScFunction]()
    for {
      injector <- EP_NAME.getExtensions
      template <- injector.injectFunctions(source)
    } try {
      val context = source match {
        case o: ScObject if o.isSyntheticObject => o.fakeCompanionClassOrCompanionClass
        case _ => source
      }
      val function = ScalaPsiElementFactory.createMethodWithContext(template, context, source)
      function.setSynthetic(context)
      function.syntheticContainingClass = Some(source)
      buffer += function
    } catch {
      case e: Throwable =>
        LOG.error(s"Error during parsing template from injector: ${injector.getClass.getName}", e)
    }
    buffer
  }

  def needsCompanion(source: ScTypeDefinition): Boolean = EP_NAME.getExtensions.exists(_.needsCompanionObject(source))
}
