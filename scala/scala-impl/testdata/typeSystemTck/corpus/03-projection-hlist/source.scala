// Concept: type-member projection through an HList-style encoding.
// Mirrors the shape of SCL-21585: substituting a type-member refinement
// through a projection type from an HList-style type.
trait Animal
class Dog extends Animal

sealed trait HList {
  type Head
  type Tail <: HList
}
trait HNil extends HList {
  type Head = Nothing
  type Tail = HNil
}
trait HCons[H, T <: HList] extends HList {
  type Head = H
  type Tail = T
}
