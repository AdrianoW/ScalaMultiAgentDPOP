import akka.actor.Actor
import akka.event.Logging

/**
 * Created by adrianowalmeida on 27/08/15.
 */
object ExActor {

  class MyActor extends Actor {
    val log = Logging(context.system, this)
    def receive = {
      case "test" => log.info("received test")
      case _      => log.info("received unknown message")
    }
  }
}
