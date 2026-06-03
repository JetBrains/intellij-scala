class C {
  def f = {}
}

new C()./* line: 2 */f

new C /* line: 2 */f

println(/* resolved: false */C.f)