package test

class Foo {
  def foo[T]: /*start*/scala.reflect.ClassTag[T]/*end*/ = ???
}
/*
package test

import scala.reflect.ClassTag

class Foo {
  def foo[T]: ClassTag[T] = ???
}
*/