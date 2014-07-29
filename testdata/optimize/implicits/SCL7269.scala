object T {
  implicit lazy val t: String = "sdf"
}

object Z {
  final implicit class Test(strVal: String) extends AnyVal {
    def test(implicit str: String): String = str
  }
}

import T.t
import Z.Test

class D {
  Seq("a") map { _.test }
}
/*
object T {
  implicit lazy val t: String = "sdf"
}

object Z {
  final implicit class Test(strVal: String) extends AnyVal {
    def test(implicit str: String): String = str
  }
}

import T.t
import Z.Test

class D {
  Seq("a") map { _.test }
}
 */