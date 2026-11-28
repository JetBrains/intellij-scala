// Concept: variance flowing through inheritance — a type argument substituted into
// a base type (asSeenFrom), and a covariant parameter propagated to a parent.
trait Animal
class Dog extends Animal
trait Producer[+A]
trait DogProducer extends Producer[Dog]
class MyColl[+A] extends Producer[A]
