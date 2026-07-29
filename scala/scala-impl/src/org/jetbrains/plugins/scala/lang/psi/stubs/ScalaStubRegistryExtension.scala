package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{ObjectStubSerializer, StubElement, StubRegistry, StubRegistryExtension, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.{Scala3ParserDefinition, ScalaElementType, ScalaParserDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScModifiersStubFactory

/**
 * Registers Scala's stub serializers/factories independently of the element types, so that element
 * types can be plain [[IElementType]]s (loadable on the Remote Development frontend) while stub support
 * lives on the backend.
 */
final class ScalaStubRegistryExtension extends ScalaStubRegistryExtensionAdapter {
  override def register(registry: StubRegistry): Unit = {
    val scalaFileType = ScalaParserDefinition.FileNodeType
    val scala3FileType = Scala3ParserDefinition.FileNodeType

    registerStubSerializer(registry, scalaFileType, new ScalaFileStubSerializer(scalaFileType))
    registerStubSerializer(registry, scala3FileType, new ScalaFileStubSerializer(scala3FileType))

    registerStubSerializingFactory(registry, ScalaElementType.MODIFIERS, new ScModifiersStubFactory(ScalaElementType.MODIFIERS))
  }
}

/**
 * A Scala adapter to handle the Java raw types, such as ObjectStubSerializer<*, *>
 */
trait ScalaStubRegistryExtensionAdapter extends StubRegistryExtension {
  def registerStubSerializer[Serializer <: ObjectStubSerializer[_ <: StubElement[_], StubElement[_]]](
    registry: StubRegistry,
    elementType: IElementType,
    serializer: Serializer,
  ): Unit = registry.registerStubSerializer(elementType, serializer)

  def registerStubSerializingFactory[Factory <: StubSerializingElementFactory[_ <: StubElement[_], _ <: PsiElement]](
    registry: StubRegistry,
    elementType: IElementType,
    factory: Factory,
  ): Unit = registry.registerStubSerializingFactory(elementType, factory)
}
