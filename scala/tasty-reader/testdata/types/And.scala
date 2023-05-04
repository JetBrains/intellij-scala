package types

trait And {
  type T1 = Int & Long

  type T2 = (Int & Long) & Float

  val v1/**//*: IllegalStateException & scala.util.control.NoStackTrace*/ = /**/new IllegalStateException with scala.util.control.NoStackTrace/*???*/
}