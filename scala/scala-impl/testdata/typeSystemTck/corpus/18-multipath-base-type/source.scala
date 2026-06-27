// Concept: a class reached via MULTIPLE inheritance paths with different type
// arguments — the case where `baseType` must MERGE (glb for covariant args), and
// where the old `BaseTypes.iterator(t).find(_.extractClass.contains(clazz))` would
// return just one arm (Box[Dog] or Box[Cat]) rather than the merge.
//
//   LR extends L with R, L extends Box[Dog], R extends Box[Cat]
//   LR baseType Box  ==  Box[Dog with Cat]   (covariant -> glb of the args)
trait Animal
class Dog extends Animal
class Cat extends Animal

trait Box[+A]
trait L extends Box[Dog]
trait R extends Box[Cat]
trait LR extends L with R

// contravariant analogue: lub of the args
trait Sink[-A]
trait LS extends Sink[Dog]
trait RS extends Sink[Cat]
trait LRS extends LS with RS
