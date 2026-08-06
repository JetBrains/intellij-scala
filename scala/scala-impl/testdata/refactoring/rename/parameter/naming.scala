trait StatefulKnowledgeSession
trait MTCEvent
class UpdateFactProcessor[F, K](session: StatefulKnowledgeSession, keyExtractor: (F) => K) {
  val z = /*caret*/keyExtractor
}
val mtcEventFactProcessor = new UpdateFactProcessor[MTCEvent, (String, String)](null, keyExtractor = (event: MTCEvent) => null )
/*
trait StatefulKnowledgeSession
trait MTCEvent
class UpdateFactProcessor[F, K](session: StatefulKnowledgeSession, NameAfterRename: (F) => K) {
  val z = /*caret*/NameAfterRename
}
val mtcEventFactProcessor = new UpdateFactProcessor[MTCEvent, (String, String)](null, NameAfterRename = (event: MTCEvent) => null )
*/