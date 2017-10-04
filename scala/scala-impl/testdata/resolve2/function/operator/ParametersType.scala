object O {
  def foo(p: Int) = {}
}

println(O /* applicable: false */ foo "1")
