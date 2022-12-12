package org.jetbrains.plugins.scala.codeInspection

import com.intellij.ide.scratch.ScratchFileService
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.assertFalse

import scala.jdk.CollectionConverters.CollectionHasAsScala

package object declarationRedundancy {

  private[declarationRedundancy] def deleteAllGlobalScratchFiles(): Unit =
    inWriteAction {
      for {
        scratchFileFolder <- ScratchFileService.getAllRootPaths.asScala
        scratchFile <- scratchFileFolder.getChildren
      } {
        scratchFile.delete(null)
        scratchFile.delete(null)
        assertFalse(s"Can't delete scratch file: $scratchFile", scratchFile.exists())
      }
    }
}
