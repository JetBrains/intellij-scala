// Concept: existential types / wildcards (Scala 2: `_` and `forSome`). Conformance
// is variance-aware containment; baseTypeSeq of an existential.
import scala.language.existentials

trait Animal
class Dog extends Animal
class Box[A]
