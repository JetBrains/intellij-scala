// Notification message: Rearranged imports
import scala.language.implicitConversions

class ImplicitClass {
  import Mess.{a, s, foo, AAAA, BBBB}

  val x = new AAAA
  val y = new BBBB
  val z = a + s + foo
}

object Mess {
  val a = 1
  val s = "a"
  def foo = 1

  class AAAA
  class BBBB

  implicit class PrintableString(val s: String) extends AnyVal {
    def println() = Predef.println(s)
  }
}

/*
import scala.language.implicitConversions

class ImplicitClass {
  import Mess.{AAAA, BBBB, a, foo, s}

  val x = new AAAA
  val y = new BBBB
  val z = a + s + foo
}

object Mess {
  val a = 1
  val s = "a"
  def foo = 1

  class AAAA
  class BBBB

  implicit class PrintableString(val s: String) extends AnyVal {
    def println() = Predef.println(s)
  }
}
*/