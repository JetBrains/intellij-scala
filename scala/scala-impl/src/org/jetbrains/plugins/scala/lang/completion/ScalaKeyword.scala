package org.jetbrains.plugins.scala
package lang
package completion

/**
 * Represents a Scala keyword. Constants defined in this trait represent all
 * keywords of the Scala language.
 */
object ScalaKeyword {

  val TRUE = "true"
  val FALSE = "false"
  val NULL = "null"
  val ABSTRACT = "abstract"
  val CASE = "case"
  val CATCH = "catch"
  val CLASS = "class"
  val DEF = "def"
  val DO = "do"
  val ELSE = "else"
  val EXTENDS = "extends"
  val FINAL = "final"
  val FINALLY = "finally"
  val FOR = "for"
  val FOR_SOME = "forSome"
  val IF = "if"
  val IMPLICIT = "implicit"
  val IMPORT = "import"
  val LAZY = "lazy"
  val MATCH = "match"
  val NEW = "new"
  val OBJECT = "object"
  val OVERRIDE = "override"
  val PACKAGE = "package"
  val PRIVATE = "private"
  val PROTECTED = "protected"
  val REQUIRES = "requires"
  val RETURN = "return"
  val SEALED = "sealed"
  val SUPER = "super"
  val THIS = "this"
  val THROW = "throw"
  val TRAIT = "trait"
  val TRY = "try"
  val TYPE = "type"
  val VAL = "val"
  val VAR = "var"
  val WHILE = "while"
  val WITH = "with"
  val YIELD = "yield"

  // Scala 3
  val DERIVES = "derives"
  val END = "end"
  val ENUM = "enum"
  val EXPORT = "export"
  val EXTENSION = "extension"
  val GIVEN = "given"
  val INFIX = "infix"
  val INLINE = "inline"
  val OPAQUE = "opaque"
  val OPEN = "open"
  val THEN = "then"
  val TRANSPARENT = "transparent"
  val USING = "using"

  val SOFT_MODIFIERS = Set(INFIX, INLINE, OPAQUE, OPEN, TRANSPARENT)

}