trait Foo {
  type A
  type B
}

object Foo {
  type Bar = Foo { type B = <ref>A }
}