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

  class PrimaryConstructor1 @inline ()

  class PrimaryConstructor2 @inline() (x: Int)

  class PrimaryProtectedConstructor1 @inline protected ()

  class PrimaryProtectedConstructor2 @inline protected (x: Int)

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