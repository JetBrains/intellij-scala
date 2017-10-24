trait ScDeclaration {
    def declaredElements: Seq[ScDeclaration]
}

trait ScValueDeclaration extends ScDeclaration

class ValueDeclarationUser {
    {
        0 match {
            case d: ScValueDeclaration => d.<ref>declaredElements
        }
    }
}