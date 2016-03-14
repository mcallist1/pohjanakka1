package zzz.akka.avionics

import akka.actor.Actor
import scala.concurrent.duration._

// A trait allowing us to create different FlightAttendants with different responsiveness-ness:
trait AttendantResponsiveness {
  val maxResponseTimeMS: Int
  def responseDuration = scala.util.Random.nextInt(maxResponseTimeMS).millis
}

object FlightAttendant {
  case class GetDrink(drinkname: String)
  case class Drink(drinkname: String)
  // Default or fallback responseTime is 5 minutes.
  def apply() = new FlightAttendant with AttendantResponsiveness { val maxResponseTimeMS = 300000 }
}

class FlightAttendant extends Actor {
  this: AttendantResponsiveness =>
  import FlightAttendant._
  // bring execution-context into implicit scope (for the Scheduler):
  implicit val ec = context.dispatcher
  def receive = {
    case GetDrink(drinkname) =>
      // use the Scheduler to decide when we are going to respond:
      context.system.scheduler.scheduleOnce(
        responseDuration, sender(), Drink(drinkname))  //the sender is passed by-value, so 'frozen' (assigned) correctly.
  }
}
