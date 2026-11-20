package tests

opaque type NameAfterRename = Int

object NameAfterRename {
  def apply(i: Int): NameAfterRename = i
  extension (b: NameAfterRename) def value: Int = b
}
