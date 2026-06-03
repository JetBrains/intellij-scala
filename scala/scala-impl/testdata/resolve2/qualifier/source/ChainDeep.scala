class C {
  def f {}
}

val v1: C = new C
val v2: C = v1
val v3: C = v2

println(v2./* line: 2 */f)

