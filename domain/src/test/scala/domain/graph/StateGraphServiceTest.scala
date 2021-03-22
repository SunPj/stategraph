package domain.graph

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

abstract class StateGraphServiceTest() extends AsyncFreeSpec with Matchers {

  protected def service: StateGraphService

  "A StateGraphService" - {
    "when empty" - {
      "should have no components" in {
        service.getGraphComponents().map { components =>
          components shouldBe empty
        }
      }

      "should upload provided components" in {
        val s = service

        val initialData = Map(
          "app" -> ComponentData(Clear, Clear, Map("cpu" -> Clear, "network" -> Clear), Set("db"), Set.empty),
          "db" -> ComponentData(NoData, NoData, Map.empty, Set.empty, Set("app"))
        )

        s.uploadComponents(initialData)

        s.getGraphComponents().map { components =>
          components should contain theSameElementsAs initialData
        }
      }

      "should recalculate own states after uploading" in {
        val s = service

        s.uploadComponents(Map(
          "app" -> ComponentData(NoData, NoData, Map("cpu" -> Clear, "network" -> Clear), Set("db", "webcamera", "paymentgateway"), Set.empty),
          "db" -> ComponentData(NoData, NoData, Map.empty, Set.empty, Set("app")),
          "webcamera" -> ComponentData(NoData, NoData, Map("hardware" -> Clear, "power" -> Alert, "network" -> Warning), Set.empty, Set("app")),
          "paymentgateway" -> ComponentData(NoData, NoData, Map("load" -> Clear, "network" -> Warning), Set.empty, Set("app"))
        ))

        s.getGraphComponents().map { components =>
          components should (have size (4))

          components should (
              contain key "app" and
              contain key "db" and
              contain key "webcamera" and
              contain key "paymentgateway"
          )

          components("app") should matchPattern { case ComponentData(Clear, _, _, _, _) => }
          components("db") should matchPattern { case ComponentData(NoData, _, _, _, _) => }
          components("webcamera") should matchPattern { case ComponentData(Alert, _, _, _, _) => }
          components("paymentgateway") should matchPattern { case ComponentData(Warning, _, _, _, _) => }
        }
      }

      "should recalculate derived states after uploading" in {
        val s = service

        s.uploadComponents(Map(
          "app" -> ComponentData(NoData, NoData, Map("cpu" -> Clear, "network" -> Clear), Set("db", "webcamera", "paymentgateway"), Set.empty),
          "db" -> ComponentData(NoData, NoData, Map.empty, Set.empty, Set("app")),
          "webcamera" -> ComponentData(NoData, NoData, Map("hardware" -> Clear, "power" -> Alert, "network" -> Warning), Set("db"), Set("app")),
          "paymentgateway" -> ComponentData(NoData, NoData, Map("load" -> Clear, "network" -> Warning), Set.empty, Set("app"))
        ))

        s.getGraphComponents().map { components =>

          components should have size (4)

          components should (
            contain key "app" and
              contain key "db" and
              contain key "webcamera" and
              contain key "paymentgateway"
          )

          components("app") should matchPattern { case ComponentData(_, Alert, _, _, _) => }
          components("db") should matchPattern { case ComponentData(_, NoData, _, _, _) => }
          components("webcamera") should matchPattern { case ComponentData(_, Alert, _, _, _) => }
          components("paymentgateway") should matchPattern { case ComponentData(_, Warning, _, _, _) => }
        }
      }

    }

    val initialState = Map(
      "app" -> ComponentData(Clear, Clear, Map("cpu" -> Clear, "network" -> Clear), Set("db", "webcamera"), Set.empty),
      "db" -> ComponentData(Clear, Clear, Map("io" -> Clear), Set("filesystem"), Set("app")),
      "webcamera" -> ComponentData(Clear, Clear, Map("hardware" -> Clear, "power" -> Clear, "network" -> Clear), Set.empty, Set("app")),
      "filesystem" -> ComponentData(Clear, Clear, Map("diskspace" -> Clear, "network" -> Clear), Set.empty, Set("db"))
    )

    s"when have existing state \n $initialState " - {
      def serviceWithExistingState = {
        val serviceInstance = service
        serviceInstance.uploadComponents(initialState)
        serviceInstance
      }

      "update components tests " - {
        "should update only given components keeping others unchanged ('app' and 'paymentgateway')" in {
          val s = serviceWithExistingState

          val updateComponents = Map(
            "app" -> ComponentData(Clear, Clear, Map("cpu" -> Clear, "network" -> Clear), Set("db", "webcamera", "filesystem", "paymentgateway"), Set.empty),
            "paymentgateway" -> ComponentData(NoData, NoData, Map.empty, Set.empty, Set("app")),
          )

          s.uploadComponents(updateComponents)

          val expectedState = initialState ++ updateComponents

          s.getGraphComponents().map { components =>
            components should contain theSameElementsAs expectedState
          }
        }
      }


      "component checkState events" - {
        s"when App's 'network' check state becomes Alert " - {
          val s = serviceWithExistingState
          s(ComponentCheckStateEvent(1, "app", "network", Alert))

          "should updates App's CPU check state to Alert " in {
            s.getGraphComponents().map { components =>
              components should contain key "app"

              components("app").checkStates should contain key "network"
              components("app").checkStates("network") should be (Alert)
            }
          }

          "should set App's ownState to Alert " in {
            s(ComponentCheckStateEvent(1, "app", "network", Alert))

            s.getGraphComponents().map { components =>
              components should contain key "app"
              components("app") should matchPattern { case ComponentData(_, Alert, _, _, _) => }
            }
          }
        }

        s"when 'diskspace' check state becomes Warning " - {
          "should set Filesystem's ownStates and derived states to Alert, db's and app's derived states to Alert " in {
            val s = serviceWithExistingState
            s(ComponentCheckStateEvent(1, "filesystem", "diskspace", Warning))

            s.getGraphComponents().map { components =>
              components should contain key "filesystem"
              components("filesystem") should matchPattern { case ComponentData(Warning, Warning, _, _, _) => }

              components should contain key "db"
              components("db") should matchPattern { case ComponentData(_, Warning, _, _, _) => }

              components should contain key "app"
              components("app") should matchPattern { case ComponentData(_, Warning, _, _, _) => }
            }
          }
        }

        s"when webcamera's 'hardware' check state becomes Alert and Filesystem's 'diskspace' becomes Warning" - {
          "should Filesystem's ownStates and derived states to Alert, db's and app's derived states to Alert " in {
            val s = serviceWithExistingState
            s(ComponentCheckStateEvent(1, "filesystem", "diskspace", Warning))
            s(ComponentCheckStateEvent(1, "webcamera", "hardware", Alert))

            s.getGraphComponents().map { components =>
              components should contain key "app"
              components("app") should matchPattern { case ComponentData(_, Alert, _, _, _) => }

              components should contain key "filesystem"
              components("filesystem") should matchPattern { case ComponentData(Warning, Warning, _, _, _) => }

              components should contain key "db"
              components("db") should matchPattern { case ComponentData(_, Warning, _, _, _) => }
            }
          }
        }
      }
    }

  }
}
