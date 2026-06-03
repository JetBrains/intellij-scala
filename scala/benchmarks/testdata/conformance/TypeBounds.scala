trait T1
trait T2 extends T1
trait A[X >: T2 <: T1]
case class A[X >: T2 <: T1](val x: X)
val a: T1 =  A(new T2{}).x
// True