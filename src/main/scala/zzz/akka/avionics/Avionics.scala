package zzz.akka.avionics

import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import akka.pattern.ask
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._
// The futures created by the ask-syntax need an execution context on which to run, we use
// the default global instance:
import scala.concurrent.ExecutionContext.Implicits.global

object Avionics {
  // timeout needed for asking ("?") for the controls:
  implicit val timeout = Timeout(5.seconds)
  val system = ActorSystem("PlaneSimulation")
  //val plane = system.actorOf(Props[Plane], "Plane")   //Ch7 version
  val plane = system.actorOf(Props(Plane()), "Plane")   //after Ch8 refactoring see Artima-forum-discussion

  def main(args: Array[String]) {
    // Request plane-controls:
    val control = Await.result( (plane ? Plane.GiveMainControl).mapTo[ActorRef], 5.seconds )
    //println("mainCtrl: " + control.toString())
    // Take-off:
    system.scheduler.scheduleOnce(200.millis) {
      control ! ControlSurfaces.StickBack(1f)
    }
    // Level-out:
    system.scheduler.scheduleOnce(1.seconds) {
      control ! ControlSurfaces.StickBack(0f)
    }
    // (Ch9) Bank left:
    system.scheduler.scheduleOnce(2.seconds) {
      control ! ControlSurfaces.StickLeft(0.5f)
    }
    // (Ch9) Straight ahead:
    system.scheduler.scheduleOnce(3.seconds) {
      control ! ControlSurfaces.StickLeft(0f)
    }
    // Climb:
    system.scheduler.scheduleOnce(4.seconds) {
      control ! ControlSurfaces.StickBack(0.5f)
    }
    // Level-out:
    system.scheduler.scheduleOnce(5.seconds) {
      control ! ControlSurfaces.StickBack(0f)
    }
    // (Ch9) Bank left:
    system.scheduler.scheduleOnce(6.seconds) {
      control ! ControlSurfaces.StickRight(1.0f)
    }
    // (Ch9) Straight ahead:
    system.scheduler.scheduleOnce(7.seconds) {
      control ! ControlSurfaces.StickRight(0f)
    }
    // Shut-down:
    system.scheduler.scheduleOnce(8.seconds) {
      system.terminate()
    }
  }
}
