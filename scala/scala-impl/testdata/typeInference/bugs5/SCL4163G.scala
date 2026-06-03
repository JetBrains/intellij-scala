abstract class Monoid[A] {
  def add(x: A, y: A): A
  def unit: A
}

def sum[A](xs: List[A])(m: Monoid[A]): A =
  if (xs.isEmpty) m.unit
  else m.add(xs.head, /*start*/sum(xs.tail)(m)/*end*/)
//A