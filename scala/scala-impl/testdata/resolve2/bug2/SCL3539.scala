class Z protected (x: String) {
  def this() = this(")")
  new /* line: 1 */Z("")
}

object Z {
  new /* line: 1 */Z("")
}

class G extends /* line: 1 */Z("") {
  new /* line: 2, applicable: false, name: this */Z("")
}

object G {
  new /* line: 2, applicable: false, name: this */Z("")
}