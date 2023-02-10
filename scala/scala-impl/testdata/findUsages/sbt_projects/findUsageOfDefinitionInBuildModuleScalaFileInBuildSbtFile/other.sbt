import BuildCommons._

lazy val valInOtherSbt1 = BuildCommons.myLibraryVersion1
lazy val valInOtherSbt2 = myLibraryVersion2

lazy val otherModule = (project in file("other")).settings()
