object O {
  def foo(a: Int, b: Int) = {}
}

println(O /* applicable: false */ foo 1)
println(O /* */ foo(1, 2))