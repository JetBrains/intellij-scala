package org.jetbrains.plugins.scala.compiler.data.worksheet

object ReplMessages {

   val ReplDelimiter = "\n$\n$\n"

   val ReplStart                 = "$$worksheet$$repl$$start$$"
   val ReplChunkStart            = "$$worksheet$$repl$$chunk$$start$$"
   val ReplChunkEnd              = "$$worksheet$$repl$$chunk$$end$$"
   val ReplChunkCompilationError = "$$worksheet$$repl$$chunk$$compilation$$error$$"
   val ReplLastChunkProcessed    = "$$worksheet$$repl$$last$$chunk$$processed$$"
}
