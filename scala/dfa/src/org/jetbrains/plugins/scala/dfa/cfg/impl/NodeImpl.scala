package org.jetbrains.plugins.scala.dfa
package cfg
package impl

private abstract class NodeImpl[Info] { this: cfg.Node =>
  override type SourceInfo = Info

  final var _block: BlockImpl[Info] = _
  final var _index: Int = -1
  final var _sourceInfo: Option[SourceInfo] = _

  override final def sourceInfo: Option[SourceInfo] = _sourceInfo
  override final def index: Int = _index.ensuring(_ >= 0)

  override final def block: Block = _block.ensuring(_ != null)
  override final def graph: Graph[Info] = _block.ensuring(_ != null).graph

  override final def labelString: String = s".L$index"

  override def toString: String = asmString()

  override def asmString(showIndex: Boolean = false, showLabel: Boolean = false, maxIndexHint: Int = 99): String = {
    val builder = new StringBuilder

    if (showLabel) {
      builder ++= labelString
      builder ++= ":\n"
    }

    if (showIndex) {
      builder ++= s"%0${maxIndexHint}d ".format(index)
    }
    builder ++= asmString

    builder.result()
  }

  protected def asmString: String
}
