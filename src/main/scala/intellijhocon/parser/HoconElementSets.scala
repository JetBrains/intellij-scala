package intellijhocon.parser

import intellijhocon.Util

object HoconElementSets {

  import HoconElementType._
  import Util._

  val Path = FieldPath | ReferencePath
}
