package parameter

trait EnumCaseClass {
  enum TypeParameter {
    case Class[A]()
  }

  enum TypeParameters {
    case Class[A, B]()
  }

  enum ValueParameter {
    case Class(x: Int)
  }

  enum ValueParameters {
    case Class(x: Int, y: Long)
  }

  enum EmptyClause {
    case Class()
  }

  enum MultipleClauses {
    case Class(x: Int)(y: Long)
  }

  enum MultipleClausesEmpty1 {
    case Class()(y: Long)
  }

  enum MultipleClausesEmpty2 {
    case Class(x: Int)()
  }

  enum MultipleClausesEmpty3 {
    case Class()()
  }

  enum TypeAndValueParameters {
    case Class[A](x: Int)
  }
}