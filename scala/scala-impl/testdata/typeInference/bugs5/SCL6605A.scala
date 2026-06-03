trait SCL6605A {
  type T[+T]

  class CC extends EE

  class DD extends EE

  class EE

  type BB <: T[CC]
  type FF <: T[DD]
  val bb: BB
  val ff: FF
  val gg = if (true) bb else ff
  /*start*/gg/*end*/
}
//SCL6605A.this.T[SCL6605A.this.EE]