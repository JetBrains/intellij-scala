override K
package Y
class Aa {
  type K = Int
}
class TypeAlias extends Aa {
  val t = foo()
  <caret>
  def y(): Int = 3
}<end>
package Y

class Aa {
    type K = Int
}
class TypeAlias extends Aa {
    val t = foo()

    override type K = Int

    def y(): Int = 3
}