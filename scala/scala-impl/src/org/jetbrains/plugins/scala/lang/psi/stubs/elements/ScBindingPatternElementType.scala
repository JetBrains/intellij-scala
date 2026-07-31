package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.{ScNamingPatternImpl, ScReferencePatternImpl, ScSeqWildcardPatternImpl, ScTypedPatternImpl}

abstract class ScBindingPatternElementType[P <: ScBindingPattern](debugName: String)
  extends ScStubElementType[P](debugName)

final class ScReferencePatternElementType extends ScBindingPatternElementType[ScReferencePattern]("reference pattern") {
  override def createElement(node: ASTNode): ScReferencePattern =
    new ScReferencePatternImpl(node)
}

final class ScTypedPatternElementType extends ScBindingPatternElementType[ScTypedPattern]("typed pattern") {
  override def createElement(node: ASTNode): ScTypedPattern =
    new ScTypedPatternImpl(node)
}

final class ScNamingPatternElementType extends ScBindingPatternElementType[ScNamingPattern]("naming pattern") {
  override def createElement(node: ASTNode): ScNamingPattern =
    new ScNamingPatternImpl(node)
}

final class ScSeqWildcardPatternElementType extends ScBindingPatternElementType[ScSeqWildcardPattern]("seq wildcard pattern") {
  override def createElement(node: ASTNode): ScSeqWildcardPattern =
    new ScSeqWildcardPatternImpl(node)
}
