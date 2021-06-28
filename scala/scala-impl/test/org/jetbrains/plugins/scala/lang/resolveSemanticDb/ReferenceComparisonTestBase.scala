package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement



class ReferenceComparisonTestBase extends ComparisonTestBase {

  final override def doTest(): Unit = {
    val files = setupFiles()
    val store = SemanticDbStore(outDir)

    var problems = Seq.empty[String]

    for (file <- files.filterByType[ScalaFile]) {
      val semanticDbFile = store.files.find(_.path.contains(file.name)).get
      for (ref <- file.elements.filterByType[ScReference]) {
        val pos = textPosOf(ref)
        val refWithPos = s"${ref.refName} at ${pos.readableString} in ${file.name}"
        val resolved = ref.multiResolveScala(false).toSeq

        if (resolved.isEmpty) {
          problems :+= s"Couldn't resolve $refWithPos"
        } else {
          val semanticDbReferences = semanticDbFile.referencesAt(pos)

          for(semanticDbTarget <- semanticDbReferences.flatMap(_.symbol)) {
            val ourTargets = resolved.flatMap(r => Seq(r.element) ++ r.parentElement).filterByType[ScNamedElement]
            if (!ourTargets.exists(e => textPosOf(e.nameId) == semanticDbTarget.position)) {
              val ours = ourTargets
                .map(e => s"${e.name} at ${textPosOf(e).readableString}")
                .mkString("\n")
              problems :+= s"$refWithPos resolves to $semanticDbTarget in semanticdb, but we resolve to:\n$ours"
            }
          }
        }
      }
    }

    assert(problems.isEmpty, problems.mkString("\n"))
  }
}
