object SCL6730 {
  class BNodeId

  class RDFTriple

  class RDFGraph {
    def addTriples(triples: Set[RDFTriple]): RDFGraph = new RDFGraph
    def insertTriple(triple: RDFTriple): RDFGraph = new RDFGraph
  }

  case class Exists(fn : BNodeId => RDFGraph)
                   (implicit seed : BNodeId) extends RDFGraph {
    override def insertTriple(triple: RDFTriple) : RDFGraph = {
      Exists{case (bnode) => fn(bnode).insertTriple(triple)} //works ok with case
    }

    override def addTriples(triples: Set[RDFTriple]) : RDFGraph = {
      Exists(bnode => fn(/*start*/bnode/*end*/).addTriples(triples))
    }
  }
}
//SCL6730.BNodeId