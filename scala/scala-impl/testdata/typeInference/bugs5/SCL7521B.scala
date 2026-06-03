def foo[T](x: Int => T): T = x(1)

val z = foo {
  case 1 => 1.0
  case x: Int => 1
}

/*start*/z/*end*/
//Double