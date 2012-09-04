class InnerCompanionTraitRename {
  sealed trait Mode
  object Mode {
    case object DropAllCreate extends Mode
    case object DropCreate extends Mode
    case object Create extends Mode
    case object None extends Mode
  }

  class Instance
  ( mode : /*caret*/Mode = Mode.None )
}
/*
class InnerCompanionTraitRename {
  sealed trait NameAfterRename
  object NameAfterRename {
    case object DropAllCreate extends NameAfterRename
    case object DropCreate extends NameAfterRename
    case object Create extends NameAfterRename
    case object None extends NameAfterRename
  }

  class Instance
  ( mode : /*caret*/NameAfterRename = NameAfterRename.None )
}
*/