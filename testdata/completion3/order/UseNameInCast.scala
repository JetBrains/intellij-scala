class Frost{
}

class BadFrost extends Frost {
}

object UseNameInCast{
  val frost = (new BadFrost).asInstanceOf[<caret>]
}