object O {
  implicit val v: Int = 1
}

def f(implicit i: Int) = {}

import O._

println(/* offset: 44 */ f)