////FIXME: IDEA resolves BuildCommons when used in `project`
//// BUT: SBT doesn't resolve it
//import BuildCommons._
//
//resolvers += sbt.librarymanagement.MavenRepo(
//  s"my_name_${BuildCommons.myLibraryVersion1}_$myLibraryVersion2",
//  "my_root"
//)