import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
class Impl(val c: Context) {
  def mono = c.literalUnit
  def poly[T: c.WeakTypeTag] = c.literal(c.weakTypeOf[T].toString)
}
object Macros {
  def mono = macro <caret>Impl.mono
}