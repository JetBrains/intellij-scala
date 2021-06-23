package types

trait Refs {
  class C

  type T1 = C

  type T

  type T2 = T

  def f[A]: A

  def f(x: Int): x.type
}