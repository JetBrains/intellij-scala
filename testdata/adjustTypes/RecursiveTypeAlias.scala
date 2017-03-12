object RecursiveTypeAlias {
  val lattice = new MapLattice(new MapLattice(???))

  val x: /*start*/lattice.sublattice.Element/*end*/ = ???
}

trait Lattice {
  type Element
}

class MapLattice[+L <: Lattice](val sublattice: L) extends Lattice {
  type Element = Map[Any, sublattice.Element]
}
/*
object RecursiveTypeAlias {
  val lattice = new MapLattice(new MapLattice(???))

  val x: lattice.sublattice.Element = ???
}

trait Lattice {
  type Element
}

class MapLattice[+L <: Lattice](val sublattice: L) extends Lattice {
  type Element = Map[Any, sublattice.Element]
}
*/