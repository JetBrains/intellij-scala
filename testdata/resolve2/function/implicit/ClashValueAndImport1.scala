object O {
  implicit val v: Int = 1
}

def f(implicit i: Int) = {}

implicit val a: Int = 1
import O._

println(/* offset: 44, valid: false */ f)