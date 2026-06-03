case class MayErr[+Eee, +Aaa](e: Either[Eee, Aaa]) {
  def flatMap[B, EE >: Eee](f: Aaa => MayErr[EE, B]): MayErr[EE, B] = {
    MayErr(e.right.flatMap(a => f(a).e))
  }
  def get(x: Eee): Eee = x
}
object MayErr {
  implicit def eitherToError[Ey, EE >: Ey, Ay, AA >: Ay](e: Either[Ey, Ay]): MayErr[EE, AA] = MayErr[Ey, Ay](e)
}
class A
class C
class B extends C
val x: Option[A] = Some(new A)
val z: Product with Either[B, A] with Serializable = x.toRight(new B)

import MayErr._

z.flatMap(meta => {
  /*start*/meta/*end*/
  MayErr(z)
})
//A