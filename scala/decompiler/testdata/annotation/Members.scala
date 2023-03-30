package annotation

trait Members {
  @inline
  class C

  @inline
  trait T

  @inline
  object O

  @inline
  def f1: Int

  @inline
  def f2: Int = ???

  class PrimaryConstructor @inline ()

  class PrimaryProtectedConstructor @inline protected ()

  class AuxiliaryConstructor {
    @inline
    def this(x: Int) = /**/this()/*???*/
  }

  @inline
  val v1: Int

  @inline
  val v2: Int = ???

  @inline
  var v3: Int

  @inline
  var v4: Int = ???

  @inline
  type X

  @inline
  type A = Int
}