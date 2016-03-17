package zzz.akka.avionics

/**
  * Created by murmeister on 12.3.2016.
  */

import akka.actor.{Actor, ActorLogging, ActorRef, Props, }
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
  import scala.concurrent.ExecutionContext.Implicits.global

  val cfgstr = "zzz.akka.avionics.flightcrew"

  // Use Altimeter-companion-object's apply method to create the Actor:  //gets refactored away in Ch8 ??
  val altimeter = context.actorOf(Props(Altimeter()), "Altimeter") // Altimeter's ActorRef now a child of Plane

  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)), "ControlSurfaces")

  val config = context.system.settings.config

  val pilotName = config.getString(s"$cfgstr.pilotName")
  val copilotName = config.getString(s"$cfgstr.copilotName")
  val attendantName = config.getString(s"$cfgstr.leadAttendantName")

  println("PlaneA")
  //val pilot = context.actorOf(Props[Pilot], pilotName)     //gets refactored away in Ch8 ??
  println("PlaneB")
  //val copilot = context.actorOf(Props[Copilot], copilotName)    //gets refactored away in Ch8 ??

  // val autopilot = context.actorOf(Props[Autopilot], "Autopilot")  //not implemented in book, at least not in Ch.7

  //val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), attendantName)  //gets refactored away in Ch8 ??

  // Need an implicit Timeout when using ask ("?") method.
  implicit val askTimeout = Timeout(1.second)

  def actorForControls(name: String) = {
    var controlsActor: ActorRef =  context.system.deadLetters
    for (afc <- context.system.actorSelection("/user/Plane/Equipment/" + name).resolveOne()) yield ActorsLocated(afc)
    println("controlsActor: " + controlsActor.toString())
    controlsActor
  }

  def actorForPilots(name: String) = {
    var pilotActor: ActorRef = context.system.deadLetters
    println("HelloPlane.actorForPilotsA")
    for (afc <- context.system.actorSelection("/user/Plane/Pilots/" + name).resolveOne()) yield pilotActor
    println("HelloPlane.actorForPilotsB")
    println("pilotActor: " + pilotActor.toString())
    pilotActor
  }

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
    Await.result(controls ? WaitForStart, 1.second) // blocking (Await) is ok for Plane start-up
  }

  def startPeople(): Unit = {
    val plane = self
    // We depend on the Actor structure beneath the Plane by using actorSelection().
    // Hopefully this is change-resilient, since we'll be the ones making the changes...
    val controls = actorForControls("ControlSurfaces")
    // val autopilot = actorForControls("Autopilot")    // Autopilot not implemented
    val altimeter = actorForControls("Altimeter")
    val people = context.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        // Subclass has to implement:
        override def childStarter(): Unit = {
          // These children get implicitly added to the hierarchy:
          println("HelloPlane.startPeople.childStarterA")
          context.actorOf(Props(newPilot(plane, controls, altimeter)), pilotName)
          println("HelloPlane.startPeople.childStarterB")
          context.actorOf(Props(newCopilot(plane, altimeter)), copilotName)
          println("HelloPlane.startPeople.childStarterC")
          //context.actorOf(Props(newPilot(plane, autopilot, controls, altimeter)), pilotName)   // Autopilot not implemented
          //context.actorOf(Props(newCopilot(plane, autopilot, altimeter)), copilotName)         // Autopilot not implemented
        }
      }), "Pilots"
    )
    // Use default strategy for flight attendants i.e. restart indefinitely:
    context.actorOf(Props(newLeadFlightAttendant), attendantName)
    Await.result(people ? WaitForStart, 1.second)  // blocking ok for Plane start-up
  }

  override def preStart(): Unit = {
    import EventSource.RegisterListener
    import Pilots.ReadyToGo
    //Ch7 implementation, refactored out in Ch8
    //altimeter ! RegisterListener(self)
    //List(pilot, copilot) foreach {
    //    _ ! Pilots.ReadyToGo
    //}

    // Get the children started. Starting order matters:
    startEquipment()
    startPeople()
    // Bootstrap the rest of the ActorSystem:
    actorForControls("Altimeter") ! RegisterListener(self)
    println("HelloPlane.preStartA")
    actorForPilots(pilotName) ! ReadyToGo
    actorForPilots(copilotName) ! ReadyToGo
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
