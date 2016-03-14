package zzz.akka.avionics

import akka.actor.{Actor, ActorRef}

// ControlSurfaces-companion-object offers the messages that can be sent
// to ControlSurfaces to control the plane:
object ControlSurfaces {
  // stick-metric is in range [-1, 1], though Altimeter will normalise the range anyway if not
  case class StickBack(amount: Float)
  case class StickForward(amount: Float)
}

// ControlSurfaces Actor needs an ActorRef to the Altimeter.
class ControlSurfaces(altimeter: ActorRef) extends Actor {
  import ControlSurfaces._
  import Altimeter._

  def receive = {
    // Pilot has pulled stick back, tell Altimeter we are climbing:
    case StickBack(amount) =>
      altimeter ! RateChange(amount)
    // Pilot has pushed stick forward, tell Altimeter we are descending:
    case StickForward(amount) =>
      altimeter ! RateChange(-1 * amount)
  }
}
