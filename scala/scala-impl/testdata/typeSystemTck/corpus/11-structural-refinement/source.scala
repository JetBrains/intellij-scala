// Concept: structural refinements with method and val members; structural
// conformance (a nominal type conforms to a structural type with the same members,
// but not vice versa).
trait Animal
class Dog extends Animal
trait Named {
  def name: String
}
