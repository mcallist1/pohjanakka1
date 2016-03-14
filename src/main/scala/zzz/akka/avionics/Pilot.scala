package zzz.akka.avionics

/**
  * Updated code to get round deprecated ActorContext.actorFor syntax taken from
  * https://github.com/danluu/akka-concurrency-wyatt/blob/master/src/main/scala/Plane.scala
  * See also this discussion:
  * http://stackoverflow.com/questions/22951549/how-do-you-replace-actorfor
  */
import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.agent.Agent
import zzz.akka.avionics.Plane.GiveMeControl

//add to build.sbt libraryDependencies: "com.typesafe.akka" %% "akka-agent" % akkaVersion,
import akka.pattern.ask
import akka.util.Timeout
import akka.routing.FromConfig
//import akka.routing.RoundRobinRouter   //deprecated, so try replacing with:
import akka.routing.RoundRobinRoutingLogic

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import  scala.concurrent.ExecutionContext.Implicits.global


object Pilots {
  case object ReadyToGo
  case object RelinquishControl
}

class Pilot extends Actor {
  import Pilots._
  import Plane._
  // murmelssonic add in companion object for ControlSurfaces:
  import ControlSurfaces._
  // murmelssonic add in companion object for Terminated Actors:
  import akka.actor.Terminated._
  var controls: ActorRef = context.system.deadLetters
  var copilot: ActorRef = context.system.deadLetters
  var autopilot: ActorRef = context.system.deadLetters

  val copilotName = context.system.settings.config.getString(
    "zzz.akka.avionics.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      context.parent ! GiveMeControl
      //copilot = context.actorFor("../" + copilotName)  //deprecated, use actorSelection instead:
      for (cop <- context.actorSelection("../" + copilotName).resolveOne()) yield copilot
      for (aut <- context.actorSelection("../Autopilot").resolveOne()) yield autopilot

    // case Controls(controlSurfaces => controls = controlSurfaces   //this doesn't mean anything.
  }
}
