package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import com.intellij.util.io.DataInputOutputUtil
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException

import java.io.DataOutput
import scala.util.control.NonFatal

final case class ScFunExprCompilerRef(line: Int) extends CompilerRef {
  import ScFunExprCompilerRef._

  override def `override`(i: Int): CompilerRef = ???

  override def save(dataOutput: DataOutput): Unit = try {
    dataOutput.writeByte(ScFunExprMarker)
    DataInputOutputUtil.writeINT(dataOutput, line)
  } catch { case NonFatal(e) => throw new BuildDataCorruptedException(e.getMessage) }
}

object ScFunExprCompilerRef {
  val ScFunExprMarker: Byte = 127
}
