trait SCL1771First[M[_], A] {
  def ∗[B](f: A => M[B]): M[B]
}
(null: SCL1771First[Option, Int]).∗ {x => Some(/*start*/x + x/*end*/)}
//Int