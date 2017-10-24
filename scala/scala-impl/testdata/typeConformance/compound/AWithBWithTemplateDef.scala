trait A
trait B

val AB: A with B = new A with B {def a = 1}
// True