// Concept: singleton types of terms, literal types, and path-dependent member
// selection through a stable term path.
trait Animal
class Dog extends Animal
trait Box {
  type T
  def get: T
}
object Registry {
  val dog: Dog = new Dog
  val boxOfDog: Box { type T = Dog } = ???
}
