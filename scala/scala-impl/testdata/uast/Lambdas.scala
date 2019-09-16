object Lambdas {
  def lambdasUsage(): Unit = {
    val seq = Seq.empty[Int]

    val lambda = (x: Int) => x + 1

    seq.map(lambda)
    seq.map(lambda _)
    seq.map(lambda(_))

    seq.map(x => x + 1) // explicit
    seq.map { x =>
      x - 1
    }
    seq.map(_ + 1) // underscore
    seq.map {
      _ - 1
    }
    seq.map(_.toString) // underscore with method call
    seq.map { _.hashCode() }
    seq.collect { // partial lambda
      case _: String => 1
    }
    seq.foreach(println) // method value

    seq.foreach(println(_)) // underscore inside method call

    seq.foreach(println _) // method with argument list omitted
  }
}
