package com.github.mumoshu

// An example of good-code-is-red.

object SCL4529 extends App {
  // The type `Foo` is red, but the code compiles.
  val ng1: /* */Foo = "foo"

  // `Foo` is still red.
  val ng2: com.github.mumoshu./* */Foo = "foo"

  // `Bar` is also red.
  val ng3: /* */Bar = "bar"

  // `Baz` is also red.
  val ng4: /* */Baz = new /* */Baz {}

  // This is OK.
  val qualified: foo./* */Foo = "foo"
}

// The situation.

package foo {

trait Imports {
  // `Foo` and `Baz` are going to be publicized via `com.github.mumoshu` package object.

  type Foo = String

  trait Baz
}

// I guess the code below is problematic for the Scala plugin.
// If you comment this out, all the red codes (The types `Foo` and `Bar` above) are gone.
object `package` extends Imports {
  trait Test {
    type Foo = String
  }
}

}

package bar {

trait Imports {
  // This is going to be publicized via `com.github.mumoshu` package object.
  type Bar = String
}

}

// Now, types `Foo` and `Bar` are provided in the `com.github.mumoshu` package.
object `package` extends foo.Imports with bar.Imports