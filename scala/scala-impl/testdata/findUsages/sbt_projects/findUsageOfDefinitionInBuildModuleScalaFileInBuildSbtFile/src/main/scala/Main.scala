object Main {
  def main(args: Array[String]): Unit = {
    //NOTE: these are expected to not be resolved
    //So these "usages" are expected to not be found
    println(BuildCommons.myLibraryVersion1)
    import BuildCommons._
    println(myLibraryVersion2)
  }
}