trait A
trait B
trait C {
  type a = Int
}
val AB: A with B { type a } = new A with B with C 
// True