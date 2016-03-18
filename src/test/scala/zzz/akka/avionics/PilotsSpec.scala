package zzz.akka.avionics

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

class FakePilot extends Actor {
  override def receive = {
    case _ =>
  }
}

object PilotsSpec {
  val copilotName = "Mari"
  val pilotName = "Marko"
  val configStr = s"""
    zzz.akka.avionics.flightcrew.copilotName = "$copilotName"
    zzz.akka.avionics.flightcrew.pilotName = "$pilotName""""
}

class PilotsSpec extends TestKit(ActorSystem("PilotsSpec",
          ConfigFactory.parseString(PilotsSpec.configStr)))
            with ImplicitSender
            with WordSpecLike
            with Matchers {
  import PilotsSpec._
  import Plane._
  // To use resolveOne()-method, we need an implicit value for parameter timeout:
  implicit val timeout = Timeout(2.seconds)

  // We create a dummy-Actor as a TestProbe instance:
  def nilActor: ActorRef = TestProbe().ref
  // Also we have two ActorPaths:
  val pilotPath = s"/user/TestPilots/$pilotName"
  val copilotPath = s"/user/TestPilots/$copilotName"

  // Helper function to construct needed hierarchy for this test-scenario:
  def pilotsReadyToGo(): ActorRef = {
    // Timeout value for an ask:
    implicit val timeout = Timeout(4.seconds)

    val a = system.actorOf(
      Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
        // Subclass has to implement:
        override def childStarter(): Unit = {
          context.actorOf(Props[FakePilot], pilotName)
          context.actorOf(Props(new Copilot(testActor, nilActor)), copilotName)
        }
      }), "TestPilots")
    // Wait for mailboxes of children-Actors to be up n running:
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)
    // Tell Copilot that she is all set to fly:
    Await.result(for (cop <- system.actorSelection(copilotPath).resolveOne()) yield cop, 2.seconds) ! Pilots.ReadyToGo
    a
  }

  "Copilot" should {
    "take control when the Pilot is terminated" in {
      pilotsReadyToGo()

      // Now we kill off the Pilot stub-Actor by sending him a PoisonPill:
      Await.result(for (fpl <- system.actorSelection(pilotPath).resolveOne()) yield fpl, 2.seconds) ! PoisonPill
      // Since this test class "is" the Plane around here (it's not, but it stands in for a Plane),
      // so we expect to see a request to this class for the controls:
      expectMsg(GiveMeControl)
      // and that request should come from the Copilot, because she is reacting to case Terminated of
      // the Pilot Actor she is context.watch-ing:
      lastSender should be {Await.result(for (cop <- system.actorSelection(copilotPath).resolveOne()) yield cop, 2.seconds)}
    }
  }
}
