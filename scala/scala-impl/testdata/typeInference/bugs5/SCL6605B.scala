trait SCL6605B {
  class A
  class B
  class C
  type A1 <: A
  type A2 <: A1
  type A3 <: A2
  type A4 <: B
  type A5 <: C
  val a2: A2 with A5
  val a3: A3 with A4
  val zz = if (true) a2 else a3
  /*start*/zz/*end*/
}
//SCL6605B.this.A2