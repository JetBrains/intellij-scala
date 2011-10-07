implement foo
package test

object A {
  object Nested {
    class Nested2
  }
}

abstract class B {
  def foo(v: A.Nested.Nested2)
}

class C extends B {
  <caret>
}<end>
package test

import test.A.Nested.Nested2

object A {

  object Nested {

    class Nested2

  }

}

abstract class B {
  def foo(v: A.Nested.Nested2)
}

class C extends B {
  def foo(v: Nested2) = null
}