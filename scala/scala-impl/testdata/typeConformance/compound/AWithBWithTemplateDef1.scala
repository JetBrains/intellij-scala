trait A
trait B

val AB: A with B { def a: Int } = new A with B {def a: Any = 1}
// False