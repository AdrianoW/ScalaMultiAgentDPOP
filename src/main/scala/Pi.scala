/**
 * Created by adrianowalmeida on 27/08/15.
 */


import akka.actor._
import akka.routing.RoundRobinPool

import scala.concurrent.duration._


object Pi extends App{

  calculate(nrOfWorkers = 5, nrOfElements = 10000, nrOfMessages = 5)

  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) {
    // Create an Akka system
    val system = ActorSystem("PiSystem")

    // create the result listener, which will print the result and shutdown the system
    val listener = system.actorOf(Props[Listener], name = "listener")

    // create the master
    val master = system.actorOf(Props(new Master(
      nrOfWorkers, nrOfMessages, nrOfElements, listener)),
      name = "master")

    // start the calculation
    master ! Calculate

  }

  sealed trait PiMessage
  case object Calculate extends PiMessage
  case class Work(start: Int, nrOfElements: Int) extends PiMessage
  case class Result(value: Double) extends PiMessage
  case class PiApproximation(pi: Double, duration: Duration)

  class Worker extends Actor {

    // calculatePiFor ...
    def calculatePiFor(start: Int, nrOfElements: Int): Double = {
      println(context.self)
      var acc = 0.0
      for (i <- start until (start + nrOfElements))
        acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
      acc
    }

    // message handler
    def receive = {
      case Work(start, nrOfElements) =>
        sender ! Result(calculatePiFor(start, nrOfElements)) // perform the work
    }
  }

  class Master(nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, listener: ActorRef)
    extends Actor {

    var pi: Double = _
    var nrOfResults: Int = _
    val start: Long = System.currentTimeMillis



    val workerRouter = context.actorOf(
      RoundRobinPool(nrOfElements).props(Props[Worker]),
      "workerRouter")

    // the messages
    def receive = {
      case Calculate =>
      for (i <- 0 until nrOfMessages) workerRouter ! Work(i * nrOfElements, nrOfElements)
      case Result(value) =>
        pi += value
        nrOfResults += 1
        if (nrOfResults == nrOfMessages) {
          // Send the result to the listener
          listener ! PiApproximation(pi, duration = (System.currentTimeMillis - start).millis)
          // Stops this actor and all its supervised children
          context.stop(self)
        }
    }
  }

  class Listener extends Actor {
    def receive = {
      case PiApproximation(pi, duration) ⇒
        println("\n\tPi approximation: \t\t%s\n\tCalculation time: \t%s"
          .format(pi, duration))

        // shut down the whole system
        context.system.shutdown()
    }
  }
}
