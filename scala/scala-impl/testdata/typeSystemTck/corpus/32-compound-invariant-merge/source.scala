// Concept: same-class merge for an INVARIANT type parameter in a compound type.
// `I[Dog] with I[Cat]` is accepted by scalac (the SLS §3.4 reduced-union rule would
// make it an error). mergePrefixAndArgs gives the invariant position an existential
// bounded by the glb and lub of the arguments, so the member `get` seen from `x` is
// Animal. Type inference for `pick(x)` instead matches the first parent: Dog.
// SPEC-GAPS.md §3.
trait Animal
class Dog extends Animal
class Cat extends Animal
trait I[A] { def get: A = ??? }
object Use {
  val x: I[Dog] with I[Cat] = null
  def pick[A](i: I[A]): A = ???
  /*ANCHOR inUse*/
}
