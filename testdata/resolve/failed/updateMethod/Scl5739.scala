object Scl5739 extends TestJava {
  private val location = new Updatable

  <ref>location() = 3
}
class Updatable {
  def update(i: Int): Unit = {
    println(i)
  }
}

