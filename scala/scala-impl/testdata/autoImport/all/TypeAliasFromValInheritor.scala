trait MyAliases {
  type MyType = String
}

trait Abc {
  trait API extends MyAliases
}

trait Abc2 extends Abc {
  trait API extends super.API
  val api: API = ???
}

object AbcImpl extends Abc2

object Test {
  val m: /*ref*/MyType = ???
}
/*
import AbcImpl.api.MyType

trait MyAliases {
  type MyType = String
}

trait Abc {
  trait API extends MyAliases
}

trait Abc2 extends Abc {
  trait API extends super.API
  val api: API = ???
}

object AbcImpl extends Abc2

object Test {
  val m: MyType = ???
}
*/