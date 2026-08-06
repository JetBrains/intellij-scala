// Notification message: Rearranged imports
class NameConflictTypeAlias {
  import Mess.{a, foo, s, AAAA, BBBB}

  val x = new AAAA
  val y = new BBBB
  val z = a + s + foo
  val l: List = null
}

object Mess {
  val a = 1
  val s = "a"
  def foo = 1

  class AAAA
  class BBBB
  type List = java.util.List
}

/*
class NameConflictTypeAlias {
  import Mess.{AAAA, BBBB, a, foo, s}

  val x = new AAAA
  val y = new BBBB
  val z = a + s + foo
  val l: List = null
}

object Mess {
  val a = 1
  val s = "a"
  def foo = 1

  class AAAA
  class BBBB
  type List = java.util.List
}
*/