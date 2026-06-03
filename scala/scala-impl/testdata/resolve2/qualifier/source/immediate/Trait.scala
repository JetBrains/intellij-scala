trait T {
  def f = {}
}

println(new T {}./* line: 2 */f)

println(new T() {}./* line: 2 */f)

println(/* resolved: false */T.f)
