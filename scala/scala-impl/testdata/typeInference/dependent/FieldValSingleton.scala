trait Evals {

  class Z {
    class Tree
  }

  val ru = new Z

  trait ToolBox[U <: Z] {
    val u: U

    def eval(tree: u.Tree): Int = 123
    def eval(s: String) = s
  }

  private lazy val evalToolBox: ToolBox[ru.type] = null

  def eval[T]: T = {
    val imported: ru.Tree = null
    /*start*/evalToolBox.eval(imported)/*end*/
    null.asInstanceOf[T]
  }
}
//Int