package testingSupport.specs2

import org.specs2.main.Arguments
import org.specs2.reporter.{Notifier, NotifierReporter, Reporter}
import org.specs2.runner.ClassRunner
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
        //TODO !!!  Worked in Specs2 2.9.2 - 1.12.2
//        exportToOthers(arguments)(spec)
        spec.executed
      }
    }
  }

  def start(arguments: Array[String]): Option[ExecutedSpecification] = classRunner.start(arguments:_*)

}