package examples

import examples.metadata.{HttpAuthentication, HttpRequestProperties, HttpResponseProperties}
import examples.basic.{AsynchronousCall, MultipleApis, OptionalParameters, SynchronousCall}
import examples.special.{ApiDiscovery, DynamicPayload, LocalCall, OneWayMessage, PositionalArguments}
import examples.customization.{ClientFunctionNames, DataStructureSerialization, ServerFunctionNames}
import examples.errorhandling.{ClientErrorMapping, HttpStatusCode, ServerErrorMapping}
import examples.integration.{AnyServer, EffectSystem, MessageCodec, RpcProtocol}
import examples.transport.{AmqpTransport, ClientTransport, EndpointTransport, ServerTransport, WebSocketTransport}
import test.base.{BaseTest, Mutex}

class ExamplesTest extends BaseTest with Mutex {

  "" - {
    "Quickstart" in {
      runTest(Quickstart)
    }
    "Basic" - {
      Seq[Any](
        AsynchronousCall,
        SynchronousCall,
        OptionalParameters,
        MultipleApis,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Integration" - {
      Seq[Any](
        AnyServer,
        EffectSystem,
        MessageCodec,
        RpcProtocol,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Transport" - {
      Seq[Any](
        ClientTransport,
        ServerTransport,
        EndpointTransport,
        WebSocketTransport,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
      testName(AmqpTransport) in {
        lock()
        try {
          AmqpTransport.main(Array())
        } finally {
          unlock()
        }
      }
    }
    "Customization" - {
      Seq[Any](
        DataStructureSerialization,
        ClientFunctionNames,
        ServerFunctionNames,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Metadata" - {
      Seq[Any](
        HttpAuthentication,
        HttpRequestProperties,
        HttpResponseProperties,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Error handling" - {
      Seq[Any](
        ClientErrorMapping,
        ServerErrorMapping,
        HttpStatusCode,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Special" - {
      Seq[Any](
        ApiDiscovery,
        DynamicPayload,
        LocalCall,
        OneWayMessage,
        PositionalArguments,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
  }

  private def testName(instance: Any): String = {
    val className = instance.getClass.getSimpleName
    className.substring(0, className.length - 1)
  }

  private def runTest(instance: Any): Unit =
    synchronized {
      val mainMethod = instance.getClass.getMethod("main", classOf[Array[String]])
      mainMethod.invoke(instance, Array[String]())
      ()
    }
}
