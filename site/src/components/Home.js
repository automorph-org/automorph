import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'
import React from 'react'
import bannerImage from '../../static/banner.jpg'

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

const integrations = [
  {
    title: 'HTTPClient',
    link: 'https://openjdk.org/groups/net/httpclient/intro.html',
  }, {
    title: 'STTP',
    link: 'docs/Plugins#client-transport',
  }, {
    title: 'Tapir',
    link: 'docs/Plugins#endpoint-transport',
  }, {
    title: 'Undertow',
    link: 'docs/Plugins#server-transport',
  }, {
    title: 'Vert.x',
    link: 'docs/Plugins#server-transport',
  }, {
    title: 'Jetty',
    link: 'docs/Plugins#server-transport',
  }, {
    title: 'Finagle',
    link: 'docs/Plugins#endpoint-transport',
  }, {
    title: 'ZIO HTTP',
    link: 'docs/Plugins#endpoint-transport',
  }, {
    title: 'Akka HTTP',
    link: 'docs/Plugins#endpoint-transport',
  }, {
    title: 'Pekko HTTP',
    link: 'docs/Plugins#endpoint-transport',
  }, {
    title: 'RabbitMQ',
    link: 'docs/Plugins#client-transport',
  }, {
    title: 'Circe',
    link: 'docs/Plugins#message-codec',
  }, {
    title: 'Jackson',
    link: 'docs/Plugins#message-codec',
  }, {
    title: 'PlayJson',
    link: 'docs/Plugins#message-codec',
  }, {
    title: 'Json4s',
    link: 'docs/Plugins#message-codec',
  }, {
    title: 'weePickle',
    link: 'docs/Plugins#message-codec',
  }, {
    title: 'uPickle',
    link: 'docs/Plugins#message-codec',
  },
]

const effects = [
  {
    title: 'Identity',
    link: 'docs/Plugins#effect-system',
  }, {
    title: 'Try',
    link: 'docs/Plugins#effect-system',
  }, {
    title: 'Future',
    link: 'docs/Plugins#effect-system',
  }, {
    title: 'ZIO',
    link: 'docs/Plugins#effect-system',
  }, {
    title: 'Monix',
    link: 'docs/Plugins#effect-system',
  }, {
    title: 'Cats Effect',
    link: 'docs/Plugins#effect-system',
  }, {
    title: 'Scalaz Effect',
    link: 'docs/Plugins#effect-system',
  },
]

const features = [
  {
    title: 'Seamless',
    link: 'docs/Examples#basic',
    description: (
      <>
        Generate type-safe <a
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
        Freely combine <a href='docs/Plugins#rpc-protocol'>RPC protocol</a>, <a
        href='docs/Plugins#message-codec'>message format</a>, <a
        href='docs/Plugins#client-transport'>transport protocol</a> and <a
        href='docs/Plugins#effect-system'>effect handling</a> layers.
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
        href='https://openjdk.java.net/'>JRE</a> 11+ and easily integrate with various libraries using <a
        href='docs/Plugins'>plugins</a>.
      </>
    ),
  }, {
    title: 'Standards',
    link: 'https://central.sonatype.com/namespace/org.automorph',
    description: tags(standards),
  }, {
    title: 'Integrations',
    link: 'https://central.sonatype.com/namespace/org.automorph',
    description: tags(integrations),
  }, {
    title: 'Effects',
    link: 'https://central.sonatype.com/namespace/org.automorph',
    description: tags(effects),
  },
]

function tags(tags) {
  return (
    <p style={{fontSize: '1.0rem', margin: '0.2rem'}}>
      {tags.map(({title, link}, index) =>
        index === tags.length - 1 ?
          <span><a href={link}>{title}</a></span> :
          <span><a href={link}>{title}</a> &bull;&nbsp;</span>
      )}
    </p>
  )
}

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

function DocumentationRow() {
  return (
    <div className='row'>
      <div className='col col--12'>
        <div className='text--center padding-bottom--lg'>
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
          <DocumentationRow/>
        </div>
      </div>
    </Layout>
  )
}

