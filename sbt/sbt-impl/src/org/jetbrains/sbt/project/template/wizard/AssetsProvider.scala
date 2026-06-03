package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.starters.local.{GeneratorAsset, GeneratorResourceFile}

final class AssetsProvider {
  // we might add different .gitignore files for different project wizards (SCL-22056)
  def getScalaIgnoreAssets: Seq[GeneratorAsset] = Seq(
    new GeneratorResourceFile(
      ".gitignore",
      getClass.getResource("/assets/ignore/scala-gitignore.txt")
    )
  )
}
