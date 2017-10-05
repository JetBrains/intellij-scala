type Pred[A] = A => Boolean

def divisibleBy(k: Int): Pred[Int] =
  n => (/*start*/n/*end*/ % k == 0)
//Int