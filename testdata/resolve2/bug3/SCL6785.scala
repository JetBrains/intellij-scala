object SCL6785 {
  class IntContainer(val anInt: Int) {
    def copy(anInt: Int): IntContainer = new IntContainer(anInt)
    def copy(s: String) = 123
  }

  object IntContainer {
    def apply(anInt: Int) = new IntContainer(anInt)
  }
  case class IntContainerContainer(intContainer: IntContainer)

  object Main {
    val intContainer = IntContainer(1)
    val anInt = IntContainerContainer(intContainer copy (/* line: 3 */anInt = 2)) // incorrectly reported error here
    def main(args: Array[String]) {
      println(anInt.intContainer.anInt)
    }
  }
}