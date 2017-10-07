package org.jetbrains.plugins.hocon.parser

import com.intellij.psi.tree.{IElementType, IFileElementType}
import org.jetbrains.plugins.hocon.lang.HoconLanguage

class HoconElementType(debugName: String) extends IElementType(debugName, HoconLanguage)

object HoconElementType {

  val HoconFileElementType = new IFileElementType("HOCON_FILE", HoconLanguage)

  /**
    * Object, i.e. object entries inside braces.
    *
    * {{{
    *   {
    *     include file("stuff")
    *     some.path = value
    *   }
    * }}}
    */
  val Object = new HoconElementType("OBJECT")

  /**
    * Contents of HOCON file or object, contains includes and object fields.
    *
    * {{{
    *   include file("stuff")
    *   some.path = value
    * }}}
    */
  val ObjectEntries = new HoconElementType("OBJECT_ENTRIES")

  /**
    * `include` clause
    *
    * {{{
    *   include file("stuff")
    * }}}
    */
  val Include = new HoconElementType("INCLUDE")

  /**
    * Thing that comes after `include` keyword.
    *
    * {{{
    *   file("stuff")
    * }}}
    */
  val Included = new HoconElementType("INCLUDED")

  /**
    * Keyed field (i.e. prefixed field or valued field) along with documentation comments.
    *
    * {{{
    *   # This doc comment is contained in an object field.
    *   # After docs comes a prefixed field or valued field (in this example - prefixed field)
    *   prefix.key = value
    * }}}
    *
    * Even if there are no doc comments, keyed field is always enclosed inside object field.
    */
  val ObjectField = new HoconElementType("OBJECT_FIELD")

  /**
    * A path-value field in which path contains more than one key:
    *
    * {{{
    *   prefix.key = value
    * }}}
    *
    * Prefixed field divides itself into first key (`prefix` in above example) and rest of the prefixed field
    * which may be another prefixed field or valued field (`key = value` in above example, which is a valued field).
    */
  val PrefixedField = new HoconElementType("PREFIXED_FIELD")

  /**
    * A key-value association (NOT path-value):
    *
    * {{{
    *    key = value
    * }}}
    */
  val ValuedField = new HoconElementType("VALUED_FIELD")

  /**
    * Path inside substitution. Divides into prefix path and last key.
    */
  val Path = new HoconElementType("PATH")

  /**
    * Key inside substitution path or keyed entry (prefixed entry or valued entry).
    */
  val Key = new HoconElementType("KEY")

  /**
    * HOCON array, i.e. brackets with sequence of values inside.
    */
  val Array = new HoconElementType("ARRAY")

  /**
    * HOCON substitution, i.e. path enclosed in `${}` (with optional `?` sign)
    */
  val Substitution = new HoconElementType("SUBSTITUTION")

  /**
    * Concatenation of two or more HOCON values.
    */
  val Concatenation = new HoconElementType("CONCATENATION")

  /**
    * Unquoted string - a concatenation of whitespace, unquoted chars and periods. This element type exists primarily
    * so that [[String]] element always has exactly one child (unquoted, quoted or multiline string).
    * Unquoted string occurs as a child of [[String]] or [[Key]].
    */
  val UnquotedString = new HoconElementType("UNQUOTED_STRING")

  /**
    * Encapsulates either an unquoted, quoted or multiline string - in value context.
    */
  val StringValue = new HoconElementType("STRING_VALUE")

  /**
    * Quoted string in `include` clause context.
    */
  val IncludeTarget = new HoconElementType("INCLUDE_TARGET")

  /**
    * Encapsulates either an unquoted, quoted or multiline string - in key context.
    */
  val KeyPart = new HoconElementType("KEY_PART")

  /**
    * Literal numeric value.
    */
  val Number = new HoconElementType("NUMBER")

  /**
    * Literal `null` value.
    */
  val Null = new HoconElementType("NULL")

  /**
    * Literal boolean value.
    */
  val Boolean = new HoconElementType("BOOLEAN")

}
