package types

trait Refs {
  trait T

  type TYPEREF = Int

  type TERMREFpkg = /**/scala./**/Long

  type TYPEREFsymbol = T

  def TYPEREFdirect[T]: T

  def TERMREFdirect(x: Int): x.type
}