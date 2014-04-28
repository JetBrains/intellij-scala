package intellijhocon

import com.intellij.psi.tree.{IElementType, IFileElementType}

class HoconElementType(debugName: String) extends IElementType(debugName, HoconLanguage)

object HoconElementType {

  object HoconFileElementType extends IFileElementType("HOCON_FILE", HoconLanguage)

  object Object extends HoconElementType("OBJECT")

  object ObjectEntries extends HoconElementType("OBJECT_ENTRIES")

  object Include extends HoconElementType("INCLUDE")

  object Included extends HoconElementType("INCLUDED")

  object ObjectField extends HoconElementType("OBJECT_FIELD")

  object Path extends HoconElementType("PATH")

  object PathElement extends HoconElementType("PATH_ELEMENT")

  object Array extends HoconElementType("ARRAY")

  object Value extends HoconElementType("VALUE")

  object Number extends HoconElementType("NUMBER")

  object Null extends HoconElementType("NULL")

  object Reference extends HoconElementType("REFERENCE")

  object Boolean extends HoconElementType("BOOLEAN")

}
