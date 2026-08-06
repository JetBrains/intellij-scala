package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserializeNodeSeq

case class KotlinData(options: Seq[CompilerOptions])

object KotlinData:
  given XmlDeserializer[KotlinData] = what =>
    val options = (what \ "compilerOptions").deserializeNodeSeq[CompilerOptions]
    Right(KotlinData(options))
