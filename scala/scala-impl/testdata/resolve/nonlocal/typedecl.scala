abstract class Container {
  type t
}


abstract class User {
    def foo(c : Container) = {
        val v : c.<ref>t = null
        3
    }
}