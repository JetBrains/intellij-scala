implicit class FileSize(val size: Long) {
  def mega: Long = size * 1024 * 1024
}

def foo() {
  println(2.<ref>mega) // RED
}
