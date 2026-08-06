case class A(a: Any)(b: Int, val c: Int) {
  /*resolved: true*/ a
  /*resolved: true*/ b
  /*resolved: true*/ c
}

val i1 = new A()(0)
i1. /*resolved: true*/ a
i1. /*resolved: false*/ b
i1. /*resolved: true*/ c

case class B(implicit a: Int) {
  /*resolved: true*/ a
}

val i2 = new B()(0)
i2. /*resolved: false*/ a
