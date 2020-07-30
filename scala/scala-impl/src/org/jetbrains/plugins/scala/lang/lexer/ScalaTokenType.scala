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

  val ClassKeyword     = new ScalaTokenType("class")
  val TraitKeyword     = new ScalaTokenType("trait")
  val EnumKeyword      = new ScalaTokenType("enum")
  val ObjectKeyword    = new ScalaTokenType("object")
  val GivenKeyword     = new ScalaTokenType("given")
  val UsingKeyword     = new ScalaTokenType("using")
  val ExtensionKeyword = new ScalaTokenType("extension")

  val NewKeyword = new ScalaTokenType("new")

  val Long    = new ScalaTokenType("long")
  val Integer = new ScalaTokenType("integer")
  val Double  = new ScalaTokenType("double")
  val Float   = new ScalaTokenType("float")

  val ExportKeyword = new ScalaTokenType("export")
  val ThenKeyword   = new ScalaTokenType("then")
  val EndKeyword    = new ScalaTokenType("end")

  val AsKeyword      = new ScalaTokenType("as")
  val DerivesKeyword = new ScalaTokenType("derives")

  val InlineKeyword      = ScalaModifierTokenType(Inline)
  val TransparentKeyword = ScalaModifierTokenType(Transparent)
  val OpaqueKeyword      = ScalaModifierTokenType(Opaque)
  val OpenKeyword        = ScalaModifierTokenType(Open)

  val SpliceStart = new ScalaTokenType("$")
  val QuoteStart  = new ScalaTokenType("'")

  val TypeLambdaArrow       = new ScalaTokenType("=>>")
  val ImplicitFunctionArrow = new ScalaTokenType("?=>")

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
