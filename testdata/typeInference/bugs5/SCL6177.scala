trait World {
  type Thing
  case class ThingHolder(thing: Thing)
}

trait WorldConsumer {
  val world: World
  val holder: world.ThingHolder = ???
}

object ourWorld extends World {
  type Thing = OurThing
}

trait OurThing {
  val x: Int
}

object ourWorldConsumer extends WorldConsumer {
  val world: ourWorld.type = ourWorld
}

{
  import ourWorldConsumer.holder.{thing}
  /*start*/thing.x/*end*/
}
//Int