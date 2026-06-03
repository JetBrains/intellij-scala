class C

object Holder {
  implicit object O extends C
}

def f(implicit p: C) = {}

import Holder._

println(/* offset: 62 */ f)

