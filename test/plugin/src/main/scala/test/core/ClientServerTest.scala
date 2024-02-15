package test.core

import test.base.{Await, Network}

trait ClientServerTest extends PlatformSpecificTest with Await with Network {

  def port(fixtureId: String): Int =
    freePort(s"${getClass.getName} / $fixtureId")
}
