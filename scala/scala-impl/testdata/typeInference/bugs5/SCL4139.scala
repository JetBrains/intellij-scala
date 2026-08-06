object SCL4139 {
  abstract class Graph {
    type Edge
    type Node <: NodeIntf
    abstract class NodeIntf {
    }
  }

  abstract class DirectedGraph extends Graph {
    type Edge <: EdgeImpl
    class EdgeImpl
    class NodeImpl extends NodeIntf {
      // with this line...
      self: Node =>
      def connectWith(node : Node): Edge = {
        /*start*/newEdge(this)/*end*/
        exit()
      }
    }
    protected def newEdge(from: Node) = 1
    protected def newEdge(from: Byte) = false
  }
}
//Int