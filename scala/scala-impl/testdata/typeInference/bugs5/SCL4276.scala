object SCL4276 {
import java.io.ByteArrayInputStream

object App extends Application {

  val streamUser = new StreamUser
  val is = ""
  /*start*/streamUser.use(is)/*end*/


}

class StreamUser {

  def use(stream: AnyRef{
    def indexOf(x: String): Int
  }) = 1
  def use(x: Boolean): Boolean = false
}
}
//Int