package zzz.akka.avionics

/**
  * Created by murmeister on 12.3.2016.
  */

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import zzz.akka.avionics.IsolatedLifeCycleSupervisor.WaitForStart
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.duration._

object Plane {
  // Returns control-surfaces of the Plane to the Actor that asks for them
  case object GiveMeControl
  // Ch7 workaround allowing Avionics main method to still request controls without Controls-wrapper:
  case object GiveMainControl
  // Response to GiveMeControl message:
  case class Controls(controls: ActorRef)

  def apply() = new Plane with AltimeterProvider with PilotProvider with LeadFlightAttendantProvider
}

// The Plane has the controls, so someone can get the controls by sending a GiveMeControl message
// to the Plane. The Plane also has the Altimeter, so we build an Altimeter also and its ActorRef
// goes into our control-surface:
class Plane extends Actor with ActorLogging {
  this: AltimeterProvider
      with PilotProvider
      with LeadFlightAttendantProvider =>
  import Altimeter._
  import EventSource._
  import Plane._

  val cfgstr = "zzz.akka.avionics.flightcrew"

  // Use Altimeter-companion-object's apply method to create the Actor:
  val altimeter = context.actorOf(Props(Altimeter()), "Altimeter")  // Altimeter's ActorRef now a child of Plane

  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)), "ControlSurfaces")

  val config = context.system.settings.config

  val pilotName = config.getString(s"$cfgstr.pilotName")
  val copilotName = config.getString(s"$cfgstr.copilotName")
  val attendantName = config.getString(s"$cfgstr.leadAttendantName")

  val pilot = context.actorOf(Props[Pilot], pilotName)

  val copilot = context.actorOf(Props[Copilot], copilotName)

  // val autopilot = context.actorOf(Props[Autopilot], "Autopilot")  //not implemented in book, at least not in Ch.7

  val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), attendantName)

  // Need an implicit Timeout when using ask ("?") method.
  implicit val askTimeout = Timeout(1.second)

  def startEquipment(): Unit = {
    val controls = context.actorOf(
      Props(new IsolatedResumeSupervisor with OneForOneStrategyFactory {
        // Subclass has to implement:
        override def childStarter(): Unit = {
          val alt = context.actorOf(Props(newAltimeter), "Altimeter")
          // These children get implicitly added to the hierarchy:
          // context.actorOf(Props(newAutopilot), "Autopilot")  // Autopilot not implemented
          context.actorOf(Props(new ControlSurfaces(alt)), "ControlSurfaces")
        }
      }), "Equipment"
    )
    Await.result(controls ? WaitForStart, 1.second)  // blocking ok for Plane start-up
  }

  def startPeople(): Unit = {
    val people = context.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        // Subclass has to implement:
        override def childStarter(): Unit = {
          // These children get implicitly added to the hierarchy:
          context.actorOf(Props(newPilot), pilotName)
          context.actorOf(Props(newCopilot), copilotName)
        }
      }), "Pilots"
    )
    // Use default strategy for flight attendants i.e. restart indefinitely:
    context.actorOf(Props(newLeadFlightAttendant), attendantName)
    Await.result(people ? WaitForStart, 1.second)  // blocking ok for Plane start-up
  }

  override def preStart(): Unit = {
    altimeter ! RegisterListener(self)
    List(pilot, copilot) foreach { _ ! Pilots.ReadyToGo }
    //pilot ! Pilots.ReadyToGo
  }

  def receive = {
    case GiveMainControl =>
      log info "Plane giving control to Main..."
      sender ! controls
    case GiveMeControl =>
      log info "Plane giving control..."
      sender ! Controls(controls)
    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")
  }
}
