object O {
  def f = {}
  object I {
    println(I.this. /* resolved: false */ f)
    println(O.this. /* offset: 17 */ f)
  }
}