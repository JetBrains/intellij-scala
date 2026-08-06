trait FOGO {
  def foo = 1
}

class OGO {
  self: FOGO =>

  val x: this.type = this

  x./* line: 2 */foo

  new OGO()./* resolved: false */foo
}