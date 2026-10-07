// Concept: lub is NOT associative. scalac's lub is n-ary (GlbLubs.lubList walks all
// operands' base type sequences at once); a `match` lubs its cases together, while a
// nested `if` lubs pairwise in the source's nesting. With refinement construction
// and depth limits the results differ: the left-nested `if` carries an extra nested
// `iterableFactory` refinement. A binary (pairwise-folding) lub can only match scalac
// on the nested shape. SLS §3.5.2 leaves lub to the compiler. SPEC-GAPS.md §4.
object Use {
  val i: Int = 0
  val c1: Boolean = true
  val c2: Boolean = true
  /*ANCHOR inUse*/
}
