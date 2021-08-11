package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication.{Output, ShellEvent}

import scala.concurrent.Future

/**
  * User: Dmitry.Naydanov
  * Date: 20.08.18.
  */
trait SbtProgramRunnerBase {
  protected def submitCommands(env: ExecutionEnvironment, state: SbtCommandLineState): Future[_] = {
    val sc = SbtShellCommunication.forProject(env.getProject)
    val commands = state.processedCommands
    
    state.getListener match {
      case Some(l) =>
        val agg = (s: StringBuilder, event: ShellEvent) => {
          event match {
            case Output(line) =>
              l(line)
              s.append("\n").append(line)
            case _ => s
          }
        }
        
        sc.command(commands, new StringBuilder(), agg)
      case _ => 
        sc.command(commands)
    }
  }
  
  protected def checkRunProfile(profile: RunProfile): Boolean = profile match {
    case sbtConf: SbtRunConfiguration if sbtConf.useSbtShell => true
    case _ => false
  }
}
