package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{ObjectStubSerializer, StubElement, StubRegistry, StubRegistryExtension, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.{Scala3ParserDefinition, ScalaElementType, ScalaParserDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements._

/**
 * Registers Scala's stub serializers/factories independently of the element types, so that element
 * types can be plain [[IElementType]]s (loadable on the Remote Development frontend) while stub support
 * lives on the backend.
 *
 * Child element types not listed here still use the legacy [[com.intellij.psi.stubs.IStubElementType]] API and are bridged
 * automatically by the platform's stub element registry (they coexist with the migrated ones).
 */
final class ScalaStubRegistryExtension extends ScalaStubRegistryExtensionAdapter {
  override def register(registry: StubRegistry): Unit = {
    val scalaFileType = ScalaParserDefinition.FileNodeType
    val scala3FileType = Scala3ParserDefinition.FileNodeType

    registerStubSerializer(registry, scalaFileType, new ScalaFileStubSerializer(scalaFileType))
    registerStubSerializer(registry, scala3FileType, new ScalaFileStubSerializer(scala3FileType))

    registerStubSerializingFactory(registry, ScalaElementType.MODIFIERS, new ScModifiersStubFactory(ScalaElementType.MODIFIERS))
    registerStubSerializingFactory(registry, ScalaElementType.SELF_TYPE, new ScSelfTypeElementStubFactory(ScalaElementType.SELF_TYPE))
    registerStubSerializingFactory(registry, ScalaElementType.TYPE_DECLARATION, new ScTypeAliasDeclarationStubFactory(ScalaElementType.TYPE_DECLARATION))
    registerStubSerializingFactory(registry, ScalaElementType.TYPE_DEFINITION, new ScTypeAliasDefinitionStubFactory(ScalaElementType.TYPE_DEFINITION))
    registerStubSerializingFactory(registry, ScalaElementType.ANNOTATION, new ScAnnotationStubFactory(ScalaElementType.ANNOTATION))
    registerStubSerializingFactory(registry, ScalaElementType.IMPORT_EXPR, new ScImportExprStubFactory(ScalaElementType.IMPORT_EXPR))
    registerStubSerializingFactory(registry, ScalaElementType.IMPORT_SELECTOR, new ScImportSelectorStubFactory(ScalaElementType.IMPORT_SELECTOR))
    registerStubSerializingFactory(registry, ScalaElementType.IMPORT_SELECTORS, new ScImportSelectorsStubFactory(ScalaElementType.IMPORT_SELECTORS))
    registerStubSerializingFactory(registry, ScalaElementType.DERIVES_CLAUSE, new ScDerivesClauseStubFactory(ScalaElementType.DERIVES_CLAUSE))
    registerStubSerializingFactory(registry, ScalaElementType.PATTERN_LIST, new ScPatternListStubFactory(ScalaElementType.PATTERN_LIST))
    registerStubSerializingFactory(registry, ScalaElementType.ANNOTATIONS, new ScAnnotationsStubFactory(ScalaElementType.ANNOTATIONS))
    registerStubSerializingFactory(registry, ScalaElementType.TEMPLATE_BODY, new ScTemplateBodyStubFactory(ScalaElementType.TEMPLATE_BODY))
    registerStubSerializingFactory(registry, ScalaElementType.EXTENSION_BODY, new ScExtensionBodyStubFactory(ScalaElementType.EXTENSION_BODY))
    registerStubSerializingFactory(registry, ScalaElementType.TEMPLATE_PARENTS, new ScTemplateParentsStubFactory(ScalaElementType.TEMPLATE_PARENTS))
    registerStubSerializingFactory(registry, ScalaElementType.FIELD_ID, new ScFieldIdStubFactory(ScalaElementType.FIELD_ID))
    registerStubSerializingFactory(registry, ScalaElementType.EARLY_DEFINITIONS, new ScEarlyDefinitionsStubFactory(ScalaElementType.EARLY_DEFINITIONS))
    registerStubSerializingFactory(registry, ScalaElementType.EXTENDS_BLOCK, new ScExtendsBlockStubFactory(ScalaElementType.EXTENDS_BLOCK))
    registerStubSerializingFactory(registry, ScalaElementType.EXTENSION, new ScExtensionStubFactory(ScalaElementType.EXTENSION))
    registerStubSerializingFactory(registry, ScalaElementType.IDENTIFIER_LIST, new ScIdListStubFactory(ScalaElementType.IDENTIFIER_LIST))
    registerStubSerializingFactory(registry, ScalaElementType.PACKAGING, new ScPackagingStubFactory(ScalaElementType.PACKAGING))
    registerStubSerializingFactory(registry, ScalaElementType.REFERENCE_PATTERN, new ScReferencePatternStubFactory(ScalaElementType.REFERENCE_PATTERN))
    registerStubSerializingFactory(registry, ScalaElementType.TYPED_PATTERN, new ScTypedPatternStubFactory(ScalaElementType.TYPED_PATTERN))
    registerStubSerializingFactory(registry, ScalaElementType.NAMING_PATTERN, new ScNamingPatternStubFactory(ScalaElementType.NAMING_PATTERN))
    registerStubSerializingFactory(registry, ScalaElementType.SEQ_WILDCARD_PATTERN, new ScSeqWildcardPatternStubFactory(ScalaElementType.SEQ_WILDCARD_PATTERN))
    registerStubSerializingFactory(registry, ScalaElementType.FUNCTION_DECLARATION, new ScFunctionDeclarationStubFactory(ScalaElementType.FUNCTION_DECLARATION))
    registerStubSerializingFactory(registry, ScalaElementType.FUNCTION_DEFINITION, new ScFunctionDefinitionStubFactory(ScalaElementType.FUNCTION_DEFINITION))
    registerStubSerializingFactory(registry, ScalaElementType.MACRO_DEFINITION, new ScMacroDefinitionStubFactory(ScalaElementType.MACRO_DEFINITION))
    registerStubSerializingFactory(registry, ScalaElementType.GIVEN_ALIAS_DECLARATION, new ScGivenAliasDeclarationStubFactory(ScalaElementType.GIVEN_ALIAS_DECLARATION))
    registerStubSerializingFactory(registry, ScalaElementType.GIVEN_ALIAS_DEFINITION, new ScGivenAliasDefinitionStubFactory(ScalaElementType.GIVEN_ALIAS_DEFINITION))
    registerStubSerializingFactory(registry, ScalaElementType.VALUE_DECLARATION, new ScValueDeclarationStubFactory(ScalaElementType.VALUE_DECLARATION))
    registerStubSerializingFactory(registry, ScalaElementType.PATTERN_DEFINITION, new ScValueDefinitionStubFactory(ScalaElementType.PATTERN_DEFINITION))
    registerStubSerializingFactory(registry, ScalaElementType.VARIABLE_DECLARATION, new ScVariableDeclarationStubFactory(ScalaElementType.VARIABLE_DECLARATION))
    registerStubSerializingFactory(registry, ScalaElementType.VARIABLE_DEFINITION, new ScVariableDefinitionStubFactory(ScalaElementType.VARIABLE_DEFINITION))
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
