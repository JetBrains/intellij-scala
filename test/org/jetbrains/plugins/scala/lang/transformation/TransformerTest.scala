package org.jetbrains.plugins.scala.lang.transformation

import org.intellij.lang.annotations.Language

/**
  * @author Pavel Fatin
  */
abstract class TransformerTest(transformer: Transformer, @Language("Scala") defaultHeader: String = "")
  extends TransformationTest(transformer.transform(_), defaultHeader)