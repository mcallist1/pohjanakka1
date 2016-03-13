package zzz.akka.avionics

/**
  * Created by murmeister on 13.3.2016.
  */

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit, TestLatch, ImplicitSender}
import scala.concurrent.duration._
import scala.concurrent.Await
import org.scalatest.{WordSpecLike, BeforeAndAfterAll}
import org.scalatest.Matchers

class AltimeterSpec extends TestKit(ActorSystem("AltimeterSpec"))
                    with ImplicitSender
                    with WordSpecLike
                    with Matchers
                    with BeforeAndAfterAll {
  import Altimeter._
  override def afterAll() { system.terminate() }

  // Instantiate an inner Helper class for each test, improving code reuse
  class Helper {
    object EventSourceSpy {
      // We define a latch to allow fast return when relevant stuff happens:
      val latch = TestLatch(1)
    }
    // Specific derivation of EventSource gives us hooks into concurrency:
    trait EventSourceSpy extends EventSource {
      def sendEvent[T](event: T): Unit = EventSourceSpy.latch.countDown()
      // Provide empty behaviour when 'processing' EventSource's messages (listener registration/de-registration):
      def eventSourceReceive = Actor.emptyBehavior
    }
    // SlicedAltimeter constructs an Altimeter with EventSourceSpy:
    def slicedAltimeter = new Altimeter with EventSourceSpy
    // Then we have a helper method that returns both the ActorRef and the underlying Actor referred to:
    def actor() = {
      val a = TestActorRef[Altimeter](Props(slicedAltimeter))
      (a, a.underlyingActor)
    }
  }

  "Altimeter" should {
    "record rate-of-climb changes" in new Helper {
      val (_, real) = actor()
      real.receive(RateChange(1f))
      real.rateOfClimb should be (real.maxRateOfClimb)
    }
    "keep rate-of-climb changes within the allowed range" in new Helper {
      val (_, real) = actor()
      real.receive(RateChange(2f))
      real.rateOfClimb should be (real.maxRateOfClimb)
    }
    "calculate altitude changes" in new Helper {
      val ref = system.actorOf(Props(Altimeter()))
      ref ! EventSource.RegisterListener(testActor)
      ref ! RateChange(1f)
      fishForMessage() {
        case AltitudeUpdate(altitude) if altitude == 0f => false
        case AltitudeUpdate(altitude) => true
      }
    }
    "send events" in new Helper {
      val (ref, _) = actor()
      Await.ready(EventSourceSpy.latch, 1.second)  //by sending an Event, the latch is opened (counted down to zero from one)
      EventSourceSpy.latch.isOpen should be (true)
    }
  }
}

