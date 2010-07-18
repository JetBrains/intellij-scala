trait A
trait B
trait C { def a: Int = 0}
val AB: A with B { def a: Int } = new A with B with C
// True