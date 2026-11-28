// Concept: type avoidance BEYOND singletons (extends 23). SLS §6.11 types a block as
// `T forSome { Q }` over its local definitions and §3.2.12 simplifies covariant
// occurrences away; scalac's packedType/existentialAbstraction gets the same results:
// - a local val's singleton in an INVARIANT position stays existential
//   (`Ref[_ <: Tree with Singleton]`, not `Ref[Tree]`: widening alone is wrong);
// - a local class is replaced by its least proper supertype (`Base`), existentially
//   in invariant positions (`Ref[_ <: Base]`) — both are the SLS's own examples;
// - a local class/object with a `this.type` member becomes a refinement type.
// SPEC-GAPS.md §6.
class Tree { def thisTree: this.type = this }
class Ref[A](val a: A)
class Base
object Use {
  /*ANCHOR inUse*/
}
