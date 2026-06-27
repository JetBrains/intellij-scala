// Concept: the top/bottom lattice — Any / AnyRef / AnyVal split, Nothing and Null
// bottoms, and value classes (extends AnyVal).
trait Animal
class Dog extends Animal
class Meters(val underlying: Int) extends AnyVal
