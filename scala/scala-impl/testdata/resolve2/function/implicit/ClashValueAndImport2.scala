object O {
  implicit val v: Int = 1
}

def f(implicit i: Int) = {}

import O._
implicit val a: Int = 1

println(/* offset: 44, applicable: false */ f)