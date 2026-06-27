// Concept: context-dependent types — `this.type`, abstract type members relative
// to `this`, and self-type references. These can only be named from *inside* the
// enclosing template, hence the /*ANCHOR*/ markers. Mirrors the shape of
// SCL-21947 (mismatching path-dependent types involving a self type).
trait Animal
class Dog extends Animal

trait Box {
  type T
  def get: T
  /*ANCHOR inBox*/
}

// Self-type: an AnimalBox *is an* Animal, but only `this` inside it witnesses that.
trait AnimalBox { self: Animal =>
  type T
  /*ANCHOR inAnimalBox*/
}
