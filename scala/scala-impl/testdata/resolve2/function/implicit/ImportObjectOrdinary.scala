class C

object Holder {
  object O extends C
}

def f(implicit p: C) = {}

import Holder._

println(/* offset: 53 */ f)

