package domain.graph

import akka.actor.typed.ActorSystem

class StateGraphActorServiceTest extends StateGraphServiceTest {

  override protected def service: StateGraphService = {
    val actor: ActorSystem[StateGraphActor.Command] = ActorSystem(StateGraphActor(), "StateGraphActor")
    implicit val scheduler = actor.scheduler
    new StateGraphActorService(actor)(scheduler)
  }
}
