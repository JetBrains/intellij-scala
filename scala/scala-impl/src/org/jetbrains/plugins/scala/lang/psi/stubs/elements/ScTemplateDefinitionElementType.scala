package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.{ASTNode, Language}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScEnumClassCaseImpl, ScEnumSingletonCaseImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScClassImpl, ScEnumImpl, ScGivenDefinitionImpl, ScObjectImpl, ScTraitImpl}

abstract class ScTemplateDefinitionElementType[TypeDef <: ScTemplateDefinition](
  debugName: String,
  language: Language = ScalaLanguage.INSTANCE
) extends ScStubElementType[TypeDef](debugName, language)

final class ScClassDefinitionElementType extends ScTemplateDefinitionElementType[ScClass]("ScClass") {
  override def createElement(node: ASTNode): ScClass = new ScClassImpl(null, this, node, toString)
}

final class ScTraitDefinitionElementType extends ScTemplateDefinitionElementType[ScTrait]("ScTrait") {
  override def createElement(node: ASTNode): ScTrait = new ScTraitImpl(null, this, node, toString)
}

final class ScObjectDefinitionElementType extends ScTemplateDefinitionElementType[ScObject]("ScObject") {
  override def createElement(node: ASTNode): ScObject = new ScObjectImpl(null, this, node, toString)
}

final class ScEnumDefinitionElementType extends ScTemplateDefinitionElementType[ScClass]("ScEnum") {
  override def createElement(node: ASTNode): ScClass = new ScEnumImpl(null, this, node, toString)
}

final class ScEnumClassCaseElementType extends ScTemplateDefinitionElementType[ScClass]("ScEnumClassCase") {
  override def createElement(node: ASTNode): ScClass = new ScEnumClassCaseImpl(null, this, node, toString)
}

final class ScEnumSingletonCaseElementType extends ScTemplateDefinitionElementType[ScObject]("ScEnumSingletonCase") {
  override def createElement(node: ASTNode): ScObject = new ScEnumSingletonCaseImpl(null, this, node, toString)
}

final class ScNewTemplateElementType extends ScTemplateDefinitionElementType[ScNewTemplateDefinition]("ScNewTemplateDefinition") {
  override def createElement(node: ASTNode): ScNewTemplateDefinition = new ScNewTemplateDefinitionImpl(null, this, node, toString)
}

final class ScGivenDefinitionElementType extends ScTemplateDefinitionElementType[ScGivenDefinition]("ScGivenDefinition") {
  override def createElement(node: ASTNode): ScGivenDefinition = new ScGivenDefinitionImpl(null, this, node, toString)
}
