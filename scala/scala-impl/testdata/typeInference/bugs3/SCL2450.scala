import scala.util.control.Exception

def loggerGetCbException(id:Long, body: => Unit) {
    /*start*/Exception.allCatch.withApply(t => {})(body)/*end*/
}
//Unit