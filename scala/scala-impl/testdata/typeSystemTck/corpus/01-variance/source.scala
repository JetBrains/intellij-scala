// Concept: variance of type constructors.
trait Animal
class Dog extends Animal
class Box[+A]          // covariant
class Inv[A]           // invariant
class Sink[-A]         // contravariant
