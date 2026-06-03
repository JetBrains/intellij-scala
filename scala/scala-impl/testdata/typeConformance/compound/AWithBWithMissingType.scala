trait A
trait B

val AB: A with B { type a } = new A with B
// False