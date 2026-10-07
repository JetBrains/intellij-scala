// Concept: component ORDER of an intersection. scalac's `=:=` compares refined-type
// parents pairwise (order-sensitive, like SLS §3.5.1 equivalence), but checks
// invariant type arguments by mutual `<:<` (Types.isSubArgs), so
// `Inv[Cat with Dog] <:< Inv[Dog with Cat]` holds while the two are not `=:=`.
// By the letter of SLS §3.5.2 (invariant args must be equivalent) the conformance
// would fail. Order matters because merged base types come out in baseTypeSeq
// (symbol-id) order, e.g. `Box[Cat with Dog]` in 18. SPEC-GAPS.md §3.
trait Animal
class Dog extends Animal
class Cat extends Animal
class Inv[A]
