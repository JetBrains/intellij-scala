package org.jetbrains.plugins.scala.lang.psi

/**
 * Names the abstract declarations and opaque definitions used by capture-bound tests.
 * Generates their source syntax and expected defining type. This is a test-only
 * model, not a production PSI classification.
 *
 * @param description human-readable member-kind label used in assertion messages
 * @param sourcePrefix declaration keywords used to generate test source:
 *                     `type` for abstract declarations or `opaque type` for opaque definitions
 */
private[lang] enum TypeAliasMemberKind(
  val description: String,
  val sourcePrefix: String
) {
  case AbstractTypeDeclaration
    extends TypeAliasMemberKind("abstract type declaration", "type")
  case OpaqueTypeAliasDefinition
    extends TypeAliasMemberKind("opaque type alias definition", "opaque type")

  /**
   * Returns the defining type expected for this member kind.
   *
   * @param aliasedTypeText defining RHS text for an opaque alias;
   *                        unused for an abstract declaration
   * @return Some(aliasedTypeText) for an opaque definition, or None for an abstract declaration
   */
  def expectedAliasedTypeText(aliasedTypeText: String): Option[String] = this match {
    case AbstractTypeDeclaration => None
    case OpaqueTypeAliasDefinition => Some(aliasedTypeText)
  }

  /**
   * Builds the defining RHS suffix, or an empty string for an abstract declaration.
   *
   * @param aliasedTypeText RHS text to append after ` = ` for an opaque definition;
   *                        unused for an abstract declaration, which has no defining RHS
   */
  def definitionSuffix(aliasedTypeText: String): String =
    expectedAliasedTypeText(aliasedTypeText) match {
      case Some(text) => s" = $text"
      case None => ""
    }
}
