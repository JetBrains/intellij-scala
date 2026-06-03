trait SCL6605C {
  type X <: Z with Y
  type Z <: A
  type Y <: B
  type H <: Z with J
  type J <: C

  trait A
  trait B
  trait C

  val x: X
  val h: H

  val u = if (true) x else h
  /*start*/u/*end*/
}
//SCL6605C.this.Z