package tests

enum Foo:
  case Bar()
  case Baz

def foo(foo: Foo) = foo<caret>
