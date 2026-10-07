// Concept: asSeenFrom of an OUTER class type parameter through an inner class that
// re-extends the outer class with a different argument. scalac walks the owner chain
// (C, then D) before consulting D's base types (AsSeenFromMap.classParameterAsSeen,
// matchesPrefixAndClass), so `A` is the outer instance's argument (String), not the
// inner re-extension's (Int). A literal reading of SLS §3.4 ("If S has a base type
// D[U1..Un] ... then Ui") finds `c.type`'s base type D[Int] first and answers Int,
// which is unsound: `c.f` returns the outer `a`, a String. SPEC-GAPS.md §2(b).
class D[A](val a: A) { class C extends D[Int](1) { def f: A = D.this.a } }
object Use {
  val d = new D[String]("s")
  val c = new d.C
  /*ANCHOR inUse*/
}
