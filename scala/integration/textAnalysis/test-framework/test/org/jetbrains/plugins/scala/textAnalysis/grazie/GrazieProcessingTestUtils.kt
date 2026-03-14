package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing

object GrazieProcessingTestUtils {

  @JvmStatic
  fun setProcessingMode(processingMode: Processing) {
    GrazieConfig.update {
      it.copy(explicitlyChosenProcessing = processingMode)
    }
  }
}
