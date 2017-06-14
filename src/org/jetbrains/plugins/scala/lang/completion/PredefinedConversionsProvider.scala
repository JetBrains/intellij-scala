package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.processor.ImplicitProcessor

/**
  * @author adkozlov
  */
abstract class PredefinedConversionsProvider {

  protected val traitQualifiedName: String

  def isValidFor(definition: ScTemplateDefinition): Boolean =
    definition.qualifiedName == traitQualifiedName

  def process(definition: ScTemplateDefinition)
             (implicit processor: ImplicitProcessor): Unit = {
    def processType: ScType => Unit = processor.processType(_, processor.getPlace)

    definition.getType().toOption
      .foreach(processType)
  }
}

object PredefinedConversionsProvider {

  val EP_NAME: ExtensionPointName[PredefinedConversionsProvider] =
    ExtensionPointName.create("org.intellij.scala.predefinedConversionsProvider")

  def conversionsProviders: Seq[PredefinedConversionsProvider] =
    EP_NAME.getExtensions

  def findConversionProvider(definition: ScTemplateDefinition): Option[PredefinedConversionsProvider] =
    conversionsProviders.find(_.isValidFor(definition))
}

class DecorateAsJavaConversionsProvider extends PredefinedConversionsProvider {

  override protected val traitQualifiedName: String =
    "scala.collection.convert.DecorateAsJava"
}

class DecorateAsScalaConversionsProvider extends PredefinedConversionsProvider {

  override protected val traitQualifiedName: String =
    "scala.collection.convert.DecorateAsScala"
}

class WrapAsJavaConversionsProvider extends PredefinedConversionsProvider {

  override protected val traitQualifiedName: String =
    "scala.collection.convert.WrapAsJava"
}

class WrapAsScalaConversionsProvider extends PredefinedConversionsProvider {

  override protected val traitQualifiedName: String =
    "scala.collection.convert.WrapAsScala"
}
