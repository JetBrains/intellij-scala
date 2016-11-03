package scala.meta.collections

/**
  * @author mucianm 
  * @since 03.06.16.
  */

trait OverloadHack1
object OverloadHack1 { implicit object Instance extends OverloadHack1 }

trait OverloadHack2
object OverloadHack2 { implicit object Instance extends OverloadHack2 }

trait OverloadHack3
object OverloadHack3 { implicit object Instance extends OverloadHack3 }

trait OverloadHack4
object OverloadHack4 { implicit object Instance extends OverloadHack4 }
