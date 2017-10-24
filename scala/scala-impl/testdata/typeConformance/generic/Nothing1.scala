// This compiles in scalac. I was trying to find a case to check the type conformance
// algorithm with the type Nothing on the RHS. Probably not a high priority to get this working.
object A {
  lazy val a: Nothing = a
}
val B: Int = A.a
// True