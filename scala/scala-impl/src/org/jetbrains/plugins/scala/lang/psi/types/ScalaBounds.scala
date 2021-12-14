package org.jetbrains.plugins.scala.lang.psi.types

trait ScalaBounds extends api.Bounds {
  typeSystem: api.TypeSystem =>

  private val scala2 = Scala2Bounds()
  private val scala3 = Scala3Bounds()

  override def glb(first: ScType, second: ScType, checkWeak: Boolean)(implicit ctx: CallContext): ScType =
    if (ctx.isScala3) scala3.glb(first, second, checkWeak)
    else              scala2.glb(first, second, checkWeak)

  override def lub(first: ScType, second: ScType, checkWeak: Boolean)(implicit ctx: CallContext): ScType =
    if (ctx.isScala3) scala3.lub(first, second, checkWeak)
    else              scala2.lub(first, second, checkWeak)
}
