object SCL2820 {
  object TypingObject extends TypingTrait {
    val columns  = List[Col](
      /*start*/Col.String('title)/*end*/
    )
  }

  trait TypingTrait {
    trait Col { def name: Symbol }
    object Col {
      case class String(name: Symbol) extends Col
    }
  }
}
//TypingObject.Col.String