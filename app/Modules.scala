import domain.graph.{StateGraphActor, StateGraphActorService, StateGraphService}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.inject._


class Modules extends AbstractModule with AkkaGuiceSupport {

  override def configure() = {
    bindTypedActor(StateGraphActor(), "StateGraphActor")
    bind(classOf[StateGraphService]).to(classOf[StateGraphActorService])
  }

}
