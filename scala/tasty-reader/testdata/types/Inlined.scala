package types

trait Inlined {
  object Foo {
    type T
  }

  transparent inline def foo1: Foo.type = /**/Foo/*???*/

  type T = /**/foo1/*Foo*/.T

  transparent inline def foo2(using foo: Foo.type): foo.type = /**/foo/*???*/

  def parameter(using /**//*x$1: */Foo.type)(x: /**/foo2/*x$1*/.T): Unit

  def result(using /**//*x$1: */Foo.type): /**/foo2/*x$1*/.T
}