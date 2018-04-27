package execcontexts

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

@Singleton
class CustomerExecutors @Inject()(system: ActorSystem) {

  val simpleDbLookups: ExecutionContext = system.dispatchers.lookup("contexts.simple-db-lookups")
  val expensiveDbLookups: ExecutionContext = system.dispatchers.lookup("contexts.expensive-db-lookups")
  val dbWriteOperations: ExecutionContext = system.dispatchers.lookup("contexts.db-write-operations")
  val expensiveCpuOperations: ExecutionContext = system.dispatchers.lookup("contexts.expensive-cpu-operations")

}
