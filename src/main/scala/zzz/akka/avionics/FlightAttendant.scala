package zzz.akka.avionics

import akka.actor.{Actor, ActorRef, Cancellable}

import scala.concurrent.duration._

// A trait allowing us to create different FlightAttendants with different responsiveness-ness:
trait AttendantResponsiveness {
  val maxResponseTimeMS: Int
  def responseDuration = scala.util.Random.nextInt(maxResponseTimeMS).millis
}

object FlightAttendant {
  case class GetDrink(drinkname: String)
  case class Drink(drinkname: String)
  case class Assist(passenger: ActorRef)
  case object Busy_?
  case object Yes
  case object No
  // Default or fallback responseTime is 5 minutes.
  def apply() = new FlightAttendant with AttendantResponsiveness { val maxResponseTimeMS = 300000 }
}

class FlightAttendant extends Actor {
  this: AttendantResponsiveness =>
  import FlightAttendant._
  // bring execution-context into implicit scope (for the Scheduler):
  implicit val ec = context.dispatcher
  // internal message to signal that drink-delivery can occur:
  case class DeliverDrink(drink: Drink)
  // store our timer, which must be an implementation of akka.actor.Cancellable':
  var pendingDelivery: Option[Cancellable] = None

  // helper method for scheduling:
  def scheduleDelivery(drinkname: String): Cancellable = {
    // from akkadocs: scheduleOnce(delay: FiniteDuration, receiver: ActorRef, msg: Any)
    context.system.scheduler.scheduleOnce(responseDuration, self, DeliverDrink(Drink(drinkname)))
  }

  // We assist injured or ill passengers when an Assist msg is matched, prioritising this behaviour over drink-delivery.
  // Return type Actor.Receive, so that it can be part of the composition of the receive()-method.
  def assistInjuredPassenger: Receive = {
    case Assist(passenger) =>
      // Cancel any pendingDeliveries of drinks and set the variable back to None:
      pendingDelivery.foreach( _.cancel() )
      pendingDelivery = None
      // Administer Getafix-potion to the patient directly (no scheduling, i.e. immediately... well, immediate send-msg):
      passenger ! Drink("Magic Healing Potion")
  }

  // Handler method for drink-requests. Schedules drink delivery and become handler for delivery to Drink-Requester,
  // unless need to become flight-doctor; respond to Busy_? msg with No:
  def handleDrinkRequests: Receive = {
    case GetDrink(drinkName) =>
      pendingDelivery = Some(scheduleDelivery(drinkName))
      context.become(assistInjuredPassenger.orElse(handleSpecificPerson(sender)))
    case Busy_? =>
      sender ! No
  }

  // Handler method for when we have become the Actor that handles delivery of drink to Drink-Requester:
  def handleSpecificPerson(person: ActorRef): Receive = {
    // If person asking us for a drink is the same person we're currently handling, we cancel the original pendingDelivery,
    // replacing it with the person's new drink-request:
    case GetDrink(drinkname) if sender == person =>
      pendingDelivery.foreach( _.cancel() )
      pendingDelivery = Some(scheduleDelivery(drinkname))
    // We can only receive and therefore process our own drink-scheduler's DeliverDrink message when in this
    // handleSpecificPerson state:
    case DeliverDrink(drink) =>
      person ! drink
      pendingDelivery = None
      // move to one of the other Receive-states:
      context.become(assistInjuredPassenger orElse handleDrinkRequests)
    // Other m-atching GetDrink-requests arriving while in this Receive-state get boomeranged (forwarded)
    //  to parent ActorRef (i.e. to LeadFlightAttendant):
    case m: GetDrink =>
      context.parent forward m
    case Busy_? =>
      sender ! Yes
  }

  def receive = assistInjuredPassenger orElse handleDrinkRequests
}
