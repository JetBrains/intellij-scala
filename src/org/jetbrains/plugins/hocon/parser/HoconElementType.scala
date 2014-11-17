package org.jetbrains.plugins.hocon.parser

import com.intellij.psi.tree.{IElementType, IFileElementType}
import org.jetbrains.plugins.hocon.lang.HoconLanguage

class HoconElementType(debugName: String) extends IElementType(debugName, HoconLanguage)

object HoconElementType {

  val HoconFileElementType = new IFileElementType("HOCON_FILE", HoconLanguage)
  val Object = new HoconElementType("OBJECT")
  val ObjectEntries = new HoconElementType("OBJECT_ENTRIES")
  val Include = new HoconElementType("INCLUDE")
  val Included = new HoconElementType("INCLUDED")
  val ObjectField = new HoconElementType("OBJECT_FIELD")
  val BareObjectField = new HoconElementType("BARE_OBJECT_FIELD")
  val FieldPath = new HoconElementType("FIELD_PATH")
  val SubstitutionPath = new HoconElementType("SUBSTITUTION_PATH")
  val Key = new HoconElementType("KEY")
  val Array = new HoconElementType("ARRAY")
  val Substitution = new HoconElementType("SUBSTITUTION")
  val Concatenation = new HoconElementType("CONCATENATION")
  val UnquotedString = new HoconElementType("UNQUOTED_STRING")
  val String = new HoconElementType("STRING")
  val Number = new HoconElementType("NUMBER")
  val Null = new HoconElementType("NULL")
  val Boolean = new HoconElementType("BOOLEAN")

}
