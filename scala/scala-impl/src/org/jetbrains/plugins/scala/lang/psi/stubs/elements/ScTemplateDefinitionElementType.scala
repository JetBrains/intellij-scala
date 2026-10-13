package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaLanguage

abstract class ScTemplateDefinitionElementType(
  debugName: String,
  language: Language = ScalaLanguage.INSTANCE
) extends ScStubElementType(debugName, language)

final class ScClassDefinitionElementType extends ScTemplateDefinitionElementType("ScClass")

final class ScTraitDefinitionElementType extends ScTemplateDefinitionElementType("ScTrait")

final class ScObjectDefinitionElementType extends ScTemplateDefinitionElementType("ScObject")

final class ScEnumDefinitionElementType extends ScTemplateDefinitionElementType("ScEnum")

final class ScEnumClassCaseElementType extends ScTemplateDefinitionElementType("ScEnumClassCase")

final class ScEnumSingletonCaseElementType extends ScTemplateDefinitionElementType("ScEnumSingletonCase")

final class ScNewTemplateElementType extends ScTemplateDefinitionElementType("ScNewTemplateDefinition")

final class ScGivenDefinitionElementType extends ScTemplateDefinitionElementType("ScGivenDefinition")
