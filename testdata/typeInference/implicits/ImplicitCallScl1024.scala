object Conv {
  // If val2List was compiled to a .class file in a library, ready in by IntelliJ, the signature looks like this:
  implicit def val2ListDepickledSignature[A >: scala.Nothing <: scala.Any](value: => A): scala.collection.immutable.List[A] = List(value)
}

import Conv.val2ListDepickledSignature
/*start*/1.length()/*end*/
//This test not work just in tests
//Int