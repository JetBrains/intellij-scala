package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserializeNodeSeq

case class JavaData(home: Option[InterpretablePath], options: Seq[CompilerOptions])

object JavaData:
  given PathConstructor[String] => XmlDeserializer[JavaData] = what =>
    val home = (what \ "home").headOption.map(e => InterpretablePath.construct(e.text))
    val options = (what \ "compilerOptions").deserializeNodeSeq[CompilerOptions]
    Right(JavaData(home, options))
