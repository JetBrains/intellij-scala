package testingSupport.specs2

import org.specs2.reporter.{NotifierReporter, Reporter, Notifier}
import org.specs2.runner.ClassRunner
import org.specs2.main.Arguments
import org.specs2.specification.{ExecutedSpecification, ExecutingSpecification}

/**
 * @author Ksenia.Sautina
 * @since 9/11/12
 */

class MyNotifierRunner(notifier: Notifier) { outer =>

  def classRunner = new ClassRunner {
    override lazy val reporter: Reporter = new NotifierReporter {
      val notifier = outer.notifier
      override def export(implicit arguments: Arguments): ExecutingSpecification => ExecutedSpecification = (spec: ExecutingSpecification) => {
        super.export(arguments)(spec)
        exportToOthers(arguments)(spec)
        spec.executed
      }
    }
  }

  def start(arguments: Array[String]) = classRunner.start(arguments:_*)

}