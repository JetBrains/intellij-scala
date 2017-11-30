class Test(numVertices: Int) {
  val parents = new Array[Short](numVertices)

  def init(): Unit = {
    java.util.Arrays.<ref>fill(parents, 0, numVertices, -1.toShort)
  }
}