class F[+A]
trait M[X]
trait N[X] {
  type MX = M[X]
  type FMX = F[MX]
}
val l: F[M[Int]] = (null: N[Int]#FMX)

// True