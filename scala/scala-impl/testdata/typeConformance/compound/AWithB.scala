trait A
trait B

// OK:
//  def AB2: A with B = new A with B
//  def AB3: A with B = new A with B { val a = 1}
//  def AB4: A with B = new A with B { type a = Any }

val AB: A with B = new A with B {def a = 1}
// True