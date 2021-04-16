trait Owner {
  trait A
  trait B {
    def bMethod(): Unit = ???
  }
  trait API {
    type Tagg = String
  }
  val api: API = ???
}

object OwnerImpl extends Owner

object Test {
  val tag: /*ref*/Tagg = ???
}
/*
import OwnerImpl.api.Tagg

trait Owner {
  trait A
  trait B {
    def bMethod(): Unit = ???
  }
  trait API {
    type Tagg = String
  }
  val api: API = ???
}

object OwnerImpl extends Owner

object Test {
  val tag: Tagg = ???
}
*/