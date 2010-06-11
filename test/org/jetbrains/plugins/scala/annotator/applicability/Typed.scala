package org.jetbrains.plugins.scala
package annotator.applicability


trait Typed extends Applicability {
  abstract override def format(definition: String, application: String) = {
    val Parameter = """(\w+):\s*(\w+)""".r
    
    val types = for(Parameter(_, t) <- Parameter.findAllIn(definition).toList) yield t
    val ids = (1 to types.size).map("T" + _)

    val id = ids.toIterator
    val typedDefinition = Parameter.replaceAllIn(definition, _ match { 
      case Parameter(n, t) => n + ": " + id.next    
    })
    
    val typeParameters = "[" + ids.mkString(", ") + "]"
    val typeArguments = "[" + types.mkString(", ") + "]"

    super.format(typeParameters + typedDefinition,  typeArguments + application)
  }
}