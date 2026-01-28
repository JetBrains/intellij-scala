package tests

class MemberExtension {
  def funMe2(p: Int): Unit = {}
}

extension (me: MemberExtension) def funMe2(p: String): Unit = {}
