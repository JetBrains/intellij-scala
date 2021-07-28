package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.psi.tree.{IElementType, TokenSet}

class ScalaTokenType(debugName: String) extends IElementType(debugName, ScalaLanguage.INSTANCE) {

  final def text: String = toString

  override final def toString: String = super.toString

  override final def isLeftBound: Boolean = true
}

//noinspection TypeAnnotation
object ScalaTokenType {

  import ScalaModifier._

  val ClassKeyword     = ScalaKeywordTokenType("class")
  val TraitKeyword     = ScalaKeywordTokenType("trait")
  val EnumKeyword      = ScalaKeywordTokenType("enum")
  val ObjectKeyword    = ScalaKeywordTokenType("object")
  val GivenKeyword     = ScalaKeywordTokenType("given")
  val UsingKeyword     = ScalaKeywordTokenType("using")
  val ExtensionKeyword = ScalaKeywordTokenType("extension")

  val NewKeyword = ScalaKeywordTokenType("new")

  val Long    = new ScalaTokenType("long")
  val Integer = new ScalaTokenType("integer")
  val Double  = new ScalaTokenType("double")
  val Float   = new ScalaTokenType("float")

  val ExportKeyword = ScalaKeywordTokenType("export")
  val ThenKeyword   = ScalaKeywordTokenType("then")
  val EndKeyword    = ScalaKeywordTokenType("end")

  val AsKeyword      = ScalaKeywordTokenType("as")
  val DerivesKeyword = ScalaKeywordTokenType("derives")

  // soft modifiers (Scala 3)
  val InlineKeyword      = ScalaModifierTokenType(Inline)
  val TransparentKeyword = ScalaModifierTokenType(Transparent)
  val OpaqueKeyword      = ScalaModifierTokenType(Opaque)
  val OpenKeyword        = ScalaModifierTokenType(Open)
  val InfixKeyword       = ScalaModifierTokenType(Infix)

  val SpliceStart = new ScalaTokenType("$")
  val QuoteStart  = new ScalaTokenType("'")

  val WildcardStar              = new ScalaTokenType("*")
  val WildcardTypeQuestionMark  = new ScalaTokenType("?")
  val TypeLambdaArrow           = new ScalaTokenType("=>>")
  val ImplicitFunctionArrow     = new ScalaTokenType("?=>") // TODO: rename to context function arrow?

  object IsTemplateDefinition {

    private[this] val tokenSet = TokenSet.create(
      ClassKeyword,
      TraitKeyword,
      EnumKeyword,
      ObjectKeyword,
      GivenKeyword,
    )

    def unapply(elementType: IElementType): Boolean =
      tokenSet.contains(elementType)
  }
}
