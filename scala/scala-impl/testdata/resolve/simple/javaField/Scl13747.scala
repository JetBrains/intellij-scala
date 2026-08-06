import java.util.Random
class State[S,A](run: S => (A, S)) {
  def map[B](f: A=>B): State[S,B] = new State ( s => {
    val (a,s2) = run(s)
    (f(a), s2)
  })
}
class RNGXX(rnd: Random) extends State[RNGXX, Int] (
  rng => (rng.nextInt, new RNGXX(rnd))
) {
  val nextInt: Int = rnd.nextInt()
}
val r1 = new RNGXX(new Random(5))

r1.<ref>map(x => x + 1)