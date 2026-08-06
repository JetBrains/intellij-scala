def m(f: Int => String) {}
m(x => x./*resolved: true*/toByte.toString)                      // ok

val v: Int => String = x => ""  // ok

val v2 = (f: Int => String) => {}
v2(x => x./*resolved: true*/toByte.toString)                     // 'x' is considered to be Nothing