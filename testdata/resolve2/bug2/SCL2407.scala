object A {
  def apply(aa: Any*) {}
}

val a = A
a(/*resolved: true, line: 2*/aa = 0)