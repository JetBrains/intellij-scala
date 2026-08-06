object SCL6008 {
  val tup = (23, 4)

  def plusOne(n: Int) = n + 1

  import tup.{_1 => number}

  plusOne(/*start*/number/*end*/)
}

//Int