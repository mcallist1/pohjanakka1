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
}

// ControlSurfaces Actor needs an ActorRef to the Altimeter.
class ControlSurfaces(altimeter: ActorRef, headInd: ActorRef) extends Actor {
  import ControlSurfaces._
  import Altimeter._
  import HeadingIndicator._    //Ch9

  def receive = {
    // Pilot has pulled stick back, tell Altimeter we are climbing:
    case StickBack(amount) =>
      altimeter ! RateChange(amount)
    // Pilot has pushed stick forward, tell Altimeter we are descending:
    case StickForward(amount) =>
      altimeter ! RateChange(-1 * amount)
    // Pilot has pushed stick left, tell HeadingIndicator we are banking left:
    case StickLeft(amount) =>
      headInd ! BankChange(-1 * amount)  // murmelsson TODO: assuming here that anti-clockwise is -ve value
    // Pilot has pushed stick right, tell HeadingIndicator we are banking right:
    case StickRight(amount) =>
      headInd ! BankChange(1 * amount)
  }
}
