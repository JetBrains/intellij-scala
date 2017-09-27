def foo(a: Int)(implicit b: String) = 0
def bar(a: Int)(b: Boolean)(implicit s: String) = 0
def bip[A](a: A)(b: A)(implicit s: String) = 0
/*start*/(foo _, bar(1) _, bar _, bip(0) _)/*end*/

// (Int => Int, Boolean => Int, Int => Boolean => Int, Int => Int)