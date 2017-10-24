trait M[I, J]
def yyy[B](y: B): M[Int, B] = null
yyy(_./*resolved: true*/toInt): M[Int, Int => Int] // red