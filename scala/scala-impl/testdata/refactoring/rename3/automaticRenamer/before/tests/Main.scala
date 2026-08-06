class Foo/*caret*/ extends Throwable

val foo: Foo = Foo()
val foo1: Foo = new Foo()

val foos: List[Foo] = List()
val foos1: Array[Foo] = Array()

@main def main(): Unit = {
  val foo: Foo = Foo()
  val someVerySpecialFoo: Foo = Foo()
  val fooAnother: Foo = new Foo()

  val anonymous = new Foo() {
  }

  val (foo1: Foo, foos: List[Foo]) = (Foo(), List[Foo]())

  try {
    for ((foo2: Foo) <- List[Foo]()) {

    }
  } catch {
    case foo: Foo =>
  }

  def local(foo: Foo): Unit = {

  }
}

def topLevel(foo: Foo): Unit = {

}

def collectionLikes(foos: List[Array[Foo]], foos2: Seq[Map[Foo, Foo]]): Unit = {

}

class FooImpl extends Foo()

object FooObj extends Foo()
