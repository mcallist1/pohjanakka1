package zzz.akka.avionics

import akka.actor.{Actor, ActorRef}

// ControlSurfaces-companion-object offers the message-types that can be sent
// to ControlSurfaces to control the plane:
object ControlSurfaces {
  // stick-metric is in range [-1, 1], though Altimeter, HeadingIndicator will normalise the range anyway if not
  case class StickBack(amount: Float)
  case class StickForward(amount: Float)
  case class StickLeft(amount: Float)   //Ch9
  case class StickRight(amount: Float)  //Ch9
  case class HasControl(somePilot: ActorRef)
}

// ControlSurfaces Actor needs an ActorRef for the Plane, the Altimeter, and the HeadingIndicator.
class ControlSurfaces(plane: ActorRef, altimeter: ActorRef, headInd: ActorRef) extends Actor {
  import ControlSurfaces._
  import Altimeter._
  import HeadingIndicator._    //Ch9

  // receive-method is defined with initial "controller" the dead-letter-office.
  // This means no significant Actor is controlling the ControlSurfaces initially:
  def receive = controlledBy(context.system.deadLetters)

  // The HasControl message shifts receive-state to whoever has control, and other
  // messages are only responded to if the sender is the controller.
  // Note this closure (over somePilot) means controlledBy() can context.become() the receive()-function.
  def controlledBy(somePilot: ActorRef): Receive = {
    // Pilot has pulled stick back, tell Altimeter we are climbing:
    case StickBack(amount) if sender == somePilot =>
      altimeter ! RateChange(amount)
    // Pilot has pushed stick forward, tell Altimeter we are descending:
    case StickForward(amount) if sender == somePilot =>
      altimeter ! RateChange(-1 * amount)
    // Pilot has pushed stick left, tell HeadingIndicator we are banking left:
    case StickLeft(amount) if sender == somePilot =>
      headInd ! BankChange(-1 * amount)  // murmelsson: assuming here that anti-clockwise is -ve value. Aha, so does the book-text.
    // Pilot has pushed stick right, tell HeadingIndicator we are banking right:
    case StickRight(amount) if sender == somePilot =>
      headInd ! BankChange(1 * amount)
    // Only the Plane can tell us who controls the Plane (via its (these) controls):
    case HasControl(entity) if sender == plane  =>
      // Become a new instance of Actor.Receive, with the controlling entity in control:
      context.become(controlledBy(entity))
  }
}
