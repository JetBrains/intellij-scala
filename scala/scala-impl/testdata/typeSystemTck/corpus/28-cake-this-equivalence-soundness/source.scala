// Concept: soundness of path/this-type EQUIVALENCE in the cake (SCL-21947 follow-up).
// The SCL-21947 fixes made IntelliJ treat `Global.this` as equivalent to ANY stable
// path whose declared class is `Global` (`other: Global`), and `Api.this` as
// equivalent to `Universe.this` whenever self types tie the two classes together.
// scalac does neither: `other` is an arbitrary `Global`, not this one, and two
// distinct this-types are distinct singletons even when one class is the other's
// self type. Positive controls: a val declared `: Global.this.type` IS this
// instance, and a member found through a self type is still spelled with the
// enclosing class's this (`Api.this.T`).
trait Api { self: Universe =>
  type T
  /*ANCHOR inApi*/
}
abstract class Universe extends Api {
  /*ANCHOR inUniverse*/
}
abstract class Global extends Universe {
  type Tree
  val other: Global
  val same: Global.this.type
  /*ANCHOR inGlobal*/
}
object Probe {
  val o: Global = null
  val p: Global = null
  /*ANCHOR inProbe*/
}
