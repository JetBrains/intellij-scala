trait Foo[A] {
  def foo: Int
}

object Conv {

  // If val2Foo[A] was compiled to a .class file in a library and read in by IntelliJ, the signature looks like this:
  implicit def val2FooDepickledSignature[A >: scala.Nothing <: scala.Any](value: => A): Foo[A] = null
}

import Conv.val2FooDepickledSignature
/*start*/1.foo/*end*/
//Int