class latitude {
  def foo(`test`: String, `cest`: Int) {}
  def foo(`test`: Int, `cest`: String) {}
  /* line: 2 */foo(cest = 123, test = "text")
}