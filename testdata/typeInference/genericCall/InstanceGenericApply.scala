class InstanceGenericApply {
  def apply[T, K](x: T, y: K): (T,K) = (x,y)
}

val g = new InstanceGenericApply

/*start*/g[Int, Double](1, 1.0)/*end*/
//(Int, Double)