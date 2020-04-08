package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.psi.tree.{IElementType, TokenSet}

class ScalaTokenType(debugName: String) extends IElementType(debugName, ScalaLanguage.INSTANCE) {

  final def text: String = toString

  override final def toString: String = super.toString

  override final def isLeftBound: Boolean = true
}

object ScalaTokenType {
  val ClassKeyword  = new ScalaTokenType("class")
  val TraitKeyword  = new ScalaTokenType("trait")
  val EnumKeyword   = new ScalaTokenType("enum")
  val ObjectKeyword = new ScalaTokenType("object")
  val GivenKeyword  = new ScalaTokenType("given")

  val NewKeyword = new ScalaTokenType("new")

  val Long    = new ScalaTokenType("long")
  val Integer = new ScalaTokenType("integer")
  val Double  = new ScalaTokenType("double")
  val Float   = new ScalaTokenType("float")

  val ExportKeyword = new ScalaTokenType("export")
  val Then          = new ScalaTokenType("then")
  val EndKeyword    = new ScalaTokenType("end")

  val As                             = new ScalaTokenType("as")
  val Derives                        = new ScalaTokenType("derives")
  val Inline: ScalaModifierTokenType = ScalaTokenTypes.kINLINE
  val Opaque                         = new ScalaTokenType("opaque")

  val SpliceStart = new ScalaTokenType("$")
  val QuoteStart  = new ScalaTokenType("'")

  val TypeLambdaArrow       = new ScalaTokenType("=>>")
  val ImplicitFunctionArrow = new ScalaTokenType("?=>")

  object IsTemplateDefinition {

    private[this] val tokenSet = TokenSet.create(
      ClassKeyword,
      TraitKeyword,
      EnumKeyword,
      ObjectKeyword
    )

    def unapply(elementType: IElementType): Boolean =
      tokenSet.contains(elementType)
  }
}
