object Test {
  {
    val `a` = 1
    a
  }
  {
    def $qmark = "?"
    /* line: 7 */?
  }
  {
    def ? = "?"
    /* line: 11 */$qmark
  }
  {
    def ∘ = "∘"
    /* line: 16 */$u2218
  }
  {
    def ∘ = "∘"
    /* line: 20 */`$u2218`
  }
  {
    def $u2218 = "$u2218"
    /* line: 24 */∘
  }
  {
    def $u2218 = "$u2218"
    /* line: 28 */`∘`
  }
}