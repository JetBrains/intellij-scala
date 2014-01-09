object SCL6235 {

  case class State[S, +A](run: S => (S, A)) {
    def flatMap[B](g: A => State[S, B]): State[S, B] = {
      State(s => {
        val (s1, a1) = run(s)
        /*start*/g(a1).run(s1)/*end*/
      })
    } //error on '}'
  }

}
//(S, B)