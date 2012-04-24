object SCL4276 {
import java.io.ByteArrayInputStream

object App extends Application {

  val streamUser = new StreamUser
  val is = new ByteArrayInputStream(new Array[Byte](10))
  /*start*/streamUser.use(is)/*end*/


}

class StreamUser {

  def use(stream: AnyRef{
    def read():Int
  }) = 1
  def use(x: Boolean): Boolean = false
}
}
//Int