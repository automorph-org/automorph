---
sidebar_position: 4
---

# Logging

## Basics

* The library performs **structured event logging** using the [SLF4J](http://www.slf4j.org/) API
* All events are logged via loggers with top-level **package** name '**automorph**'
* The log **message** itself specifies the logged **event type** only
* Event **properties** are passed into the associated [Mapped Diagnostic Context](https://www.slf4j.org/api/org/slf4j/MDC.html)
* It is thus highly recommended to **configure** the utilized **SL4J implementation** to include **MDC** in the logger output


## Log level semantics

* `ERROR` - Non-recoverable errors
* `WARN` - Recoverable errors
* `INFO` - Main events (e.g. remote API call performed successfully, component initialized)
* `DEBUG` - Diagnostic events (e.g. request received, response sent)
* `TRACE` - Detailed diagnostic events (e.g. message body, message metadata)


## Example configuration

An example logging configuration for [Logback](https://logback.qos.ch/) which supports setting the log level using the `LOG_LEVEL` environment variable:

```xml
<configuration>
  <!-- Disable Logback internal logging at startup -->
  <statusListener class="ch.qos.logback.core.status.NopStatusListener" />

  <!-- Release logging resources on application termination -->
  <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>

  <!-- Simple console logging -->
  <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%cyan(%date{HH:mm:ss.SSS}) [%highlight(%-5level)] %message - %gray(%mdc) %</pattern>
    </encoder>
  </appender>
    
  <!-- Root log level -->
  <root level="${LOG_LEVEL:-OFF}">
      <appender-ref ref="Console" />
  </root>

  <!-- Package-specific log level -->
  <logger name="automorph" level="${LOG_LEVEL:-INFO}"/>
</configuration>
```
