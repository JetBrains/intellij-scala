// Concept: a class reached via MULTIPLE inheritance paths with different type
// arguments — the case where `baseType` must MERGE (glb for covariant args, lub for
// contravariant), and where the old `BaseTypes.iterator(t).find(_.extractClass.contains(clazz))`
// would return just one arm (Box[Dog] or Box[Cat]) rather than the merge.
//
//   (L with R) baseType Box  ==  Box[Cat with Dog]   (covariant -> glb of the args)
//
// The merge is exercised through COMPOUND TYPES (`L with R`). A class template
// `trait LR extends L with R` is NOT legal Scala: scalac's RefChecks.validateBaseTypes
// enforces SLS §3.4's "reduced union" rule ("inherits different type instances of
// trait Box") — one inherited instance must conform to all others. The template
// forms below satisfy that rule by naming the merged instance explicitly.
//
// Conformance does NOT use the merged base type: `(L with R) <:< Box[Dog with Cat]`
// is false, because scalac checks a compound type on the left component-wise
// (TypeComparers.fourthTry, `parents exists (_ <:< tp2)`), as SLS §3.5.2 says,
// while `baseType`/`memberType` see the merge. See SPEC-GAPS.md §3.
trait Animal
class Dog extends Animal
class Cat extends Animal

trait Box[+A]
trait L extends Box[Dog]
trait R extends Box[Cat]
trait LRB extends L with R with Box[Dog with Cat]

// contravariant analogue: lub of the args
trait Sink[-A]
trait LS extends Sink[Dog]
trait RS extends Sink[Cat]
trait LRSB extends LS with RS with Sink[Animal]
