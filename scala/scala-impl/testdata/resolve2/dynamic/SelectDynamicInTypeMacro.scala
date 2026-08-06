import scala.language.dynamics
import scala.language.experimental.macros

trait Foo {
  type T
}

object Foo extends Dynamic {
  def selectDynamic(name: String): Foo = macro DummyMacro.impl
}

object DummyMacro {
  def impl = ???
}

type A = Foo./* file: this, name: selectDynamic */`5`./* resolved: false */T