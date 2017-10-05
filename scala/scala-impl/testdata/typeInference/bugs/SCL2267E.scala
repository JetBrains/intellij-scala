trait T
class C {
  self: T =>
  val x = ""
  /*start*/this/*end*/.x
}
()
//C.this.type