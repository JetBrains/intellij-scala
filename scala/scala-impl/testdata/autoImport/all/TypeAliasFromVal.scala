trait Owner {
  trait A
  trait B {
    def bMethod(): Unit = ???
  }
  trait API {
    type Tag = String
  }
  val api: API = ???
}

object OwnerImpl extends Owner

object Test {
  val tag: /*ref*/Tag = ???
}
/*
import OwnerImpl.api.Tag

trait Owner {
  trait A
  trait B {
    def bMethod(): Unit = ???
  }
  trait API {
    type Tag = String
  }
  val api: API = ???
}

object OwnerImpl extends Owner

object Test {
  val tag: Tag = ???
}
*/