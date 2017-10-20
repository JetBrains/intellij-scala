package org.jetbrains.plugins.scala.project

/**
  * @author Pavel Fatin
  */
sealed abstract class Platform(val name: String, private[project] val proxy: PlatformProxy) extends Named {
  override def getName: String = name

  override def toString: String = name
}

object Platform {
  val Default: Platform = Scala

  def from(proxy: PlatformProxy): Platform = proxy match {
    case PlatformProxy.Scala => Scala
    case PlatformProxy.Dotty => Dotty
  }

  val Values = Array(Scala, Dotty)

  final object Scala extends Platform("Scala", PlatformProxy.Scala)

  final object Dotty extends Platform("Dotty", PlatformProxy.Dotty)

  implicit val ordering: Ordering[Platform] = new Ordering[Platform] {
    override def compare(x: Platform, y: Platform): Int =
      Values.indexOf(x).compare(Values.indexOf(y))
  }
}