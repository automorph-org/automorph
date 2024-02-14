package test.core

import test.base.{Await, Network}

trait ClientServerTest extends PlatformClientServerTest with Await with Network {

  def port(fixtureId: String): Int =
    freePort(s"${getClass.getName} / $fixtureId")
}
