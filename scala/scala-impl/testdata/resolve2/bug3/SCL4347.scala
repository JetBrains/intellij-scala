object SCL4347 {
  trait Base {
    type Foo
    def frob(f: Foo): Foo
  }

  trait Derived {
    self: Base =>
    type Foo = Double
    def frob(f: /* line: 9 */Foo): Foo = 12
  }
}