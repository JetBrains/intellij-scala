// Concept: F-bounded polymorphism — a type parameter whose bound mentions itself.
// Stresses recursive bound substitution and asSeenFrom into a self-referential base.
trait Ord[A <: Ord[A]]
class Num extends Ord[Num]
class Str extends Ord[Str]
