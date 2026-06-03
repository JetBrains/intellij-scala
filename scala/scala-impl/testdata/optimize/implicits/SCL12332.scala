// Notification message: Removed 1 import
object SCL12332 {
  import Ordering.Implicits._
  import java.util._

  implicitly[Ordering[Seq[String]]]
}
/*
object SCL12332 {
  import Ordering.Implicits._

  implicitly[Ordering[Seq[String]]]
}
*/