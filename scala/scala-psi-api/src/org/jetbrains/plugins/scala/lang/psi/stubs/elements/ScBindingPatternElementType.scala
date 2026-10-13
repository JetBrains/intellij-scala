package org.jetbrains.plugins.scala.lang.psi.stubs.elements

abstract class ScBindingPatternElementType(debugName: String)
  extends ScStubElementType(debugName)

final class ScReferencePatternElementType extends ScBindingPatternElementType("reference pattern")

final class ScTypedPatternElementType extends ScBindingPatternElementType("typed pattern")

final class ScNamingPatternElementType extends ScBindingPatternElementType("naming pattern")

final class ScSeqWildcardPatternElementType extends ScBindingPatternElementType("seq wildcard pattern")
