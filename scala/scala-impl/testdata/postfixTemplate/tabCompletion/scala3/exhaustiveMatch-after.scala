package tests

enum Foo:
  case Bar()
  case Baz

def foo(foo: Foo) = foo match
  case Foo.Bar() =>
  case Foo.Baz =><caret>
