trait T
class C {
  self: T =>
  /*start*/this/*end*/: this.type
}
()
//C.this.type