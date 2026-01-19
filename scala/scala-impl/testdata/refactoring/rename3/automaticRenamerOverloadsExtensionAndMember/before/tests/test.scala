package tests

class MemberExtension {
  def funMe/*caret*/(p: Int): Unit = {}
}

extension (me: MemberExtension) def /*caret*/funMe(p: String): Unit = {}
