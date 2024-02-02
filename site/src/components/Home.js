import useDocusaurusContext from '@docusaurus/useDocusaurusContext'
import Layout from '@theme/Layout'
import React from 'react'
import bannerImage from '../../static/banner.jpg'

const style = {
  list: {
    listStyle: 'none',
    padding: '0'
  }
}

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
  },
  {
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
  },
  {
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
  },
  {
    title: 'Permissive',
    link: 'docs/Examples#metadata',
    description: (
        <>
          Consume or create <a
            href='docs/Examples#dynamic-payload'>dynamic message payload</a> and access or modify <a
            href='docs/Examples#metadata'>transport protocol metadata</a>.
        </>
    ),
  },
  {
    title: 'Discoverable',
    link: 'docs/Examples#api-discovery',
    description: (
        <>
          Utilize discovery functions providing <a href='https://spec.open-rpc.org'>OpenRPC</a> 1.3+ and <a
            href='https://www.openapis.org'>OpenAPI</a> 3.1+ schemas for exposed APIs.
        </>
    ),
  },
  {
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
  {
    title: 'RPC protocols',
    link: 'docs/Plugins#rpc-protocol',
    description: (
        <>
          <ul style={style.list}>
            <li><a href='https://www.jsonrpc.org/specification'>JSON-RPC</a></li>
            <li><a href='docs/Web-RPC'>Web-RPC</a></li>
          </ul>
        </>
    ),
  },
  {
    title: 'Transport protocols',
    link: 'docs/Plugins#message-transport',
    description: (
        <>
          <ul style={style.list}>
            <li><a href='docs/Examples#http-authentication'>HTTP</a></li>
            <li><a href='docs/Examples#websocket-transport'>WebSocket</a></li>
            <li><a href='docs/Examples#amqp-transport'>AMQP</a></li>
          </ul>
        </>
    ),
  },
  {
    title: 'Effect handling',
    link: 'docs/Plugins#effect-system',
    description: (
        <>
          <ul style={style.list}>
            <li><a href='docs/Examples#asynchronous-call'>Asynchronous</a></li>
            <li><a href='docs/Examples#synchronous-call'>Synchronous</a></li>
            <li><a href='docs/Examples#effect-system'>Monadic</a></li>
          </ul>
        </>
    ),
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

function FeatureCell({ title, link, description }) {
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

