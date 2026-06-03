object Test {
  {
    val `a` = 1
    a
  }
  {
    def $qmark = "?"
    /* name: $qmark, line: 7 */?
  }
  {
    def ? = "?"
    /* line: 11, name: ?*/$qmark
  }
  {
    def ? = "?"
    /* line: 15, name: ?*/$u003F
  }
  {
    def ? = "?"
    /* line: 19, name: ?*/`$u003F`
  }
  {
    def $u003F = "$u003F"
    /* line: 23, name: $u003F*/?
  }
  {
    def $u003F = "$u003F"
    /* line: 27, name: $u003F*/`?`
  }
}