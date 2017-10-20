import scalaz._
import Scalaz._

object SCL10087 {
  (\/.fromTryCatchNonFatal({ throw new RuntimeException("Hello InteliJ IDEA super luxury premium") }) <ref>>>= { _ => "Oops".right[Throwable] }) <ref>valueOr(println)
}
