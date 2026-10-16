// Concept: higher-kinded types — type-constructor parameters (F[_]), HK bounds,
// and substitution of a type constructor into a base type (Monad[M] -> Functor[M]).
trait Animal
class Dog extends Animal
trait Functor[F[_]]
trait Monad[F[_]] extends Functor[F]
class M[A]
trait Bounded[F[_ <: Animal]]   // higher-kinded bound
class Holder[A <: Animal]
