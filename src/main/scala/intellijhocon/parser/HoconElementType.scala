package intellijhocon.parser

import com.intellij.psi.tree.{IElementType, IFileElementType}
import intellijhocon.lang.HoconLanguage

class HoconElementType(debugName: String) extends IElementType(debugName, HoconLanguage)

object HoconElementType {

  val HoconFileElementType = new IFileElementType("HOCON_FILE", HoconLanguage)

  val Object = new HoconElementType("OBJECT")

  val ObjectEntries = new HoconElementType("OBJECT_ENTRIES")

  val Include = new HoconElementType("INCLUDE")

  val Included = new HoconElementType("INCLUDED")

  val ObjectField = new HoconElementType("OBJECT_FIELD")

  val Path = new HoconElementType("PATH")

  val ReferencePath = new HoconElementType("REFERENCE_PATH")

  val Key = new HoconElementType("KEY")

  val Array = new HoconElementType("ARRAY")

  val Value = new HoconElementType("VALUE")

  val UnquotedString = new HoconElementType("UNQUOTED_STRING")

  val Number = new HoconElementType("NUMBER")

  val Null = new HoconElementType("NULL")

  val Reference = new HoconElementType("REFERENCE")

  val Boolean = new HoconElementType("BOOLEAN")

}
