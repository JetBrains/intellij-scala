import scala.reflect.runtime.{universe => ru, currentMirror}
import ru._

object SCL5592 {
  def testFun(symbol :ClassSymbol) {
    /*start*/currentMirror.runtimeClass(symbol)/*end*/
  }
}
//runtime.universe.RuntimeClass