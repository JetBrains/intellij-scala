object O {
  val v: Int = 1
}

def f(implicit i: Int) = {}

import O._

println(/* offset: 35, valid: false */ f)