object O {
  def foo = {}
}

println(O /* */ foo)
println(O /* applicable: false */ foo())
println(O /* applicable: false */ foo 1)
println(O /* applicable: false */ foo (1))
println(O /* applicable: false */ foo (1, 2))