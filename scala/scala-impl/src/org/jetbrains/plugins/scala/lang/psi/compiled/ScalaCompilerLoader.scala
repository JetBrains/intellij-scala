package org.jetbrains.plugins.scala.lang.psi.compiled

import com.intellij.debugger.{DebuggerManager, NameMapper}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.compiled.ScalaCompilerLoader._
import org.jetbrains.plugins.scala.project.ProjectExt

// TODO -- It's unclear what this class is for exactly. The name mapper that is added by this loader is
//  1. Used by the API Watcher plugin when figuring out the class name of a method's containing class. But letting
//     a PSI element return the correct name in getName also works fine, so for this use case we do not need this class.
//  2. Not used by the Scala debugger afaics.
//  --
//  According to Eugene Zhuravlev
//  https://jetbrains.slack.com/archives/CMDBCUBGE/p1685451314269359?thread_ts=1685448946.062299&cid=CMDBCUBGE
//  "for some debugger operations (e.g. finding position in sources that corresponds to the current execution point) it
//  is important to know the FQ name of the class in the bytecode. For java the bytecode name can be calculated from the
//  sources FQ name, while for some languages that produce bytecode this may not be the case."
//  --
//  Since the Scala debugger is not using it, though, I think it's safe to make modifications to the name mapper.
//  It seems like the only thing that might break is debugging Scala code with the Java debugger. But the
//  ScalaNameMapper implementation we've apparently had for a while seems very incomplete. It only appends a `$` when a
//  type definition is an ScObject.
//  --
//  My impression is that if we make sure our getName and getQualifiedName implementations are correct from a Java pov,
//  we should be ok. I also think we should remove ScalaCompilerLoader and the NameMapper completely.
//

class ScalaCompilerLoader extends StartupActivity.DumbAware {
  override def runActivity(project: Project): Unit = {
    if (!isDisabled) {
      val mapper = new ScalaNameMapper
      DebuggerManager.getInstance(project).addClassNameMapper(mapper)

      invokeOnDispose(project.unloadAwareDisposable) {
        DebuggerManager.getInstance(project).removeClassNameMapper(mapper)
      }
    }
  }
}

object ScalaCompilerLoader {
  private[compiled] def isDisabled: Boolean = {
    val application = ApplicationManager.getApplication
    !application.isUnitTestMode &&
      // The following check is hardly bulletproof, however (currently) there is no API to query that
      application.getClass.getSimpleName.contains("Upsource")
  }

  private class ScalaNameMapper extends NameMapper {
    override def getQualifiedName(clazz: PsiClass): String = clazz match {
      case tmpl: ScTemplateDefinition =>
        inReadAction { javaName(tmpl) }
      case _ =>
        null
    }
  }

  private def javaName(clazz: ScTemplateDefinition): String = {
    clazz.qualifiedName/* + (if (clazz.isInstanceOf[ScObject]) "$" else "")*/
  }
}
