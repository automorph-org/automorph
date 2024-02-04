import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'
import React from 'react'
import bannerImage from '../../static/banner.jpg'

const features = [
  {
    title: 'Seamless',
    link: 'docs/Examples#basic',
    description: (
      <>
        Generate optimized <a
        href='https://en.wikipedia.org/wiki/Remote_procedure_call'>RPC</a> <a
        href='docs/Quickstart#static-client'>client</a> or <a
        href='docs/Quickstart#server'>server</a> bindings from existing
        public API methods at compile time.
      </>
    ),
  }, {
    title: 'Flexible',
    link: 'docs/Examples#dynamic-payload',
    description: (
      <>
        Customize <a
        href='docs/Examples#data-type-serialization'>data type serialization</a>, remote API <a
        href='docs/Examples#client-function-names'>function names</a>, RPC protocol <a
        href='docs/Examples#client-exceptions'>errors</a> and <a
        href='docs/Examples#http-authentication'>authentication</a>.
      </>
    ),
  }, {
    title: 'Modular',
    link: 'docs/Examples#integration',
    description: (
      <>
        Choose plugins for <a href='docs/Plugins#rpc-protocol'>RPC protocol</a>, <a
        href='docs/Plugins#effect-system'>effect handling</a>, <a
        href='docs/Plugins#client-transport'>transport protocol</a> and <a
        href='docs/Plugins#message-codec'>message format</a>.
      </>
    ),
  }, {
    title: 'Permissive',
    link: 'docs/Examples#metadata',
    description: (
      <>
        Consume or create <a
        href='docs/Examples#dynamic-payload'>dynamic message payload</a> and access or modify <a
        href='docs/Examples#metadata'>transport protocol metadata</a>.
      </>
    ),
  }, {
    title: 'Discoverable',
    link: 'docs/Examples#api-discovery',
    description: (
      <>
        Utilize discovery functions providing <a href='https://spec.open-rpc.org'>OpenRPC</a> 1.3+ and <a
        href='https://www.openapis.org'>OpenAPI</a> 3.1+ schemas for exposed APIs.
      </>
    ),
  }, {
    title: 'Compatible',
    link: 'https://central.sonatype.com/namespace/org.automorph',
    description: (
      <>
        Use with <a href='https://www.scala-lang.org/'>Scala</a> 3.3+ or 2.13+ on <a
        href='https://openjdk.java.net/'>JRE</a> 11+ and easily integrate with various popular <a
        href='docs/Plugins'>libraries</a>.
      </>
    ),
  },
]

const standards = [
  {
    title: 'JSON-RPC',
    link: 'https://www.jsonrpc.org/specification',
  }, {
    title: 'Web-RPC',
    link: 'docs/Web-RPC',
  }, {
    title: 'HTTP',
    link: 'https://en.wikipedia.org/wiki/HTTP',
  }, {
    title: 'WebSocket',
    link: 'https://en.wikipedia.org/wiki/WebSocket',
  }, {
    title: 'AMQP',
    link: 'https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol',
  }, {
    title: 'JSON',
    link: 'https://www.json.org',
  }, {
    title: 'MessagePack',
    link: 'https://msgpack.org',
  }, {
    title: 'Smile',
    link: 'https://github.com/FasterXML/smile-format-specification',
  }, {
    title: 'CBOR',
    link: 'https://cbor.io',
  }, {
    title: 'Ion',
    link: 'https://amazon-ion.github.io/ion-docs',
  },
]

const messageTransports = [
  {
  //   title: 'HTTPClient',
  //   link: 'https://openjdk.org/groups/net/httpclient/intro.html',
  // }, {
    title: 'STTP',
    link: 'https://sttp.softwaremill.com/en/latest/',
  }, {
    title: 'Tapir',
    link: 'https://tapir.softwaremill.com/',
  }, {
    title: 'Undertow',
    link: 'https://undertow.io/',
  }, {
    title: 'VertX',
    link: 'https://vertx.io/',
  }, {
    title: 'Jetty',
    link: 'https://www.eclipse.org/jetty/',
  }, {
    title: 'Finagle',
    link: 'https://twitter.github.io/finagle/',
  }, {
    title: 'Akka HTTP',
    link: 'https://doc.akka.io/docs/akka-http/current/',
  }, {
    title: 'NanoHTTPD',
    link: 'https://github.com/NanoHttpd/nanohttpd',
  }, {
    title: 'RabbitMQ',
    link: 'https://www.rabbitmq.com/java-client.html',
  }
]

const messageCodecs = [
  {
    title: 'Circe',
    link: 'https://circe.github.io/circe',
  }, {
    title: 'Jackson',
    link: 'https://github.com/FasterXML/jackson-module-scala',
  }, {
    title: 'weePickle',
    link: 'https://github.com/rallyhealth/weePickle',
  }, {
    title: 'uPickle',
    link: 'https://github.com/com-lihaoyi/upickle',
  }, {
    title: 'Argonaut',
    link: 'http://argonaut.io/doc',
  },
]

const effectSystems = [
  {
    title: 'Identity',
    link: 'https://docs.scala-lang.org/scala3/book/taste-functions.html',
  }, {
    title: 'Try',
    link: 'https://docs.scala-lang.org/scala3/book/fp-functional-error-handling.html#option-isnt-the-only-solution',
  }, {
    title: 'Future',
    link: 'https://docs.scala-lang.org/overviews/core/futures.html',
  }, {
    title: 'ZIO',
    link: 'https://zio.dev',
  }, {
    title: 'Monix',
    link: 'https://monix.io',
  }, {
    title: 'Cats Effect',
    link: 'https://typelevel.org/cats-effect',
  }, {
    title: 'Scalaz Effect',
    link: 'https://github.com/scalaz',
  },
]

function BannerRow() {
  const config = useDocusaurusContext().siteConfig
  return (
    <div className='row'>
      <div className='col col--12'>
        <div className='padding-horiz--sm'>
          <img src={bannerImage} alt={config.title}/>
        </div>
      </div>
    </div>
  )
}

function TaglineRow() {
  const config = useDocusaurusContext().siteConfig
  return (
    <div className='row'>
      <div className='col col--12'>
        <div className='text--center'>
          <p style={{
            fontSize: '1.5rem',
            color: 'var(--ifm-menu-color)',
          }}>{config.tagline}</p>
        </div>
      </div>
    </div>
  )
}

function FeatureCell({title, link, description}) {
  return (
    <div className='col col--4'>
      <div className='text--center padding-horiz--sm padding-vert--sm'>
        <h3 style={{lineHeight: '1.0rem'}}><a href={link}>{title}</a></h3>
        <p style={{fontSize: '1.0rem'}}>{description}</p>
      </div>
    </div>
  )
}

function FeaturesRow() {
  return (
    <div className='row'>
      {features.map((props, index) => (
        <FeatureCell key={index} {...props} />
      ))}
    </div>
  )
}

function tags(tags) {
  return (
    <p style={{fontSize: '1.0rem', margin: '0.2rem'}}>
      {tags.map(({title, link}, index) =>
        index === tags.length - 1 ?
          <span><a href={link}>{title}</a></span> :
          <span><a href={link}>{title}</a>&nbsp;&bull;&nbsp;</span>
      )}
    </p>
  )
}

function TagsRow() {
  return (
    <div className='row'>
      <div className='col col--12'>
        <div className='text--center'>
          {tags(standards)}
          {tags(messageTransports)}
          {tags(effectSystems)}
          {tags(messageCodecs)}
        </div>
      </div>
    </div>
  )
}

function DocumentationRow() {
  return (
    <div className='row'>
      <div className='col col--12'>
        <div className='text--center padding-top--lg padding-bottom--lg'>
          <a className='button' href='docs/Quickstart' style={{
            color: 'var(--sidebar-background-color)',
            backgroundColor: 'var(--ifm-link-color)',
            fontSize: '1.2rem',
          }}>
            Get Started
          </a>
        </div>
      </div>
    </div>
  )
}

export function Home() {
  const config = useDocusaurusContext().siteConfig
  return (
    <Layout title='Home' description={config.tagline}>
      <div style={{
        backgroundColor: 'var(--sidebar-background-color)',
      }}>
        <div className='container'>
          <BannerRow/>
          <TaglineRow/>
          <FeaturesRow/>
          <TagsRow/>
          <DocumentationRow/>
        </div>
      </div>
    </Layout>
  )
}

