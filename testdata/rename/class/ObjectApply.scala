object OuterObjectApply {
  object Objecta {
    def apply(s: String) = s + "biaka"

    def foo(s: String) = Objecta(s)
  }

  object Main {
    def main(args: Array[String]) {
      print( /*caret*/ Objecta.foo("ti "))
    }
  }
}
/*
object OuterObjectApply {
  object NameAfterRename {
    def apply(s: String) = s + "biaka"

    def foo(s: String) = NameAfterRename(s)
  }

  object Main {
    def main(args: Array[String]) {
      print( /*caret*/ NameAfterRename.foo("ti "))
    }
  }
}
*/