def xxx[B](y: B): Tuple2[Int, B] = null
xxx(_./*resolved: true*/toInt): Tuple2[Int, Int => Int] // red
xxx(_./*resolved: true*/toInt): (Int, Int => Int)       // red