# nifi-flow-over-tcp

![CI Status](https://github.com/EndzeitBegins/nifi-flow-over-tcp/actions/workflows/gradle.yml/badge.svg)
[![Qodana](https://github.com/EndzeitBegins/nifi-flow-over-tcp/actions/workflows/code_quality.yml/badge.svg)](https://github.com/EndzeitBegins/nifi-flow-over-tcp/actions/workflows/code_quality.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.endzeitbegins/nifi-flow-over-tcp?color=ff69b4)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.github.endzeitbegins%22%20AND%20a%3A%22nifi-flow-over-tcp%22)

Use this project to transfer your [Apache NiFi][nifi] FlowFiles 
from one cluster to another, using bare TCP connections.

The standard processors `PutTCP` and `ListenTCP` only transmit the content but not the attributes of a FlowFile 
and create a new FlowFile whenever a delimiter, by default a line-break, is encountered.
In contrast, the processors of this project provide easy means of transferring whole FlowFiles, 
retaining both FlowFile attributes and FlowFile contents, from one host to another.

For example, one can use this to transfer FlowFiles over a unidirectional network / gateway / through a data diode,
as long as `ACK` packets from the receiving site are allowed through.

## Get started

### Installation

Download the `.nar` of the latest release from [maven-central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.github.endzeitbegins%22%20AND%20a%3A%22nifi-flow-over-tcp%22).

There are multiple ways of integrating a `.nar` archive into a NiFi cluster,
as outlined in the [NiFi System Administrator’s Guide](https://nifi.apache.org/docs/nifi-docs/html/administration-guide.html#processor-locations).

#### Compatibility

| nifi-flow-over-tcp                    | Apache NiFi |
| ------------------------------------- | ----------- |
| 0.7.0                                 | 1.20.0      |
| 0.6.0 <br> 0.5.0                      | 1.19.1      |
| 0.4.0 <br> 0.3.0 <br> 0.2.0 <br> 0.1.0| 1.18.0      |

### Integration

The project contains multiple processors. 
After installation of the `.nar` archive, they can be dragged onto the Flow canvas like any other processor.

#### PutFlowToTCP

Use this processor to transmit FlowFiles, that is both attributes and content, from the incoming connection
to the NiFi host at the configured host and port.
The receiving NiFi host should listen for TCP packets using a `ListenFlowFromTCP` processor.

A simple codec is used to transmit the FlowFiles over TCP.

| byte-length | purpose                                                 | example - hex (dec)                | example - utf-8  |
|-------------|---------------------------------------------------------|------------------------------------|------------------|
| 4           | size in bytes of attributes as utf-8 encoded JSON (= m) | 0x00000010 (16)                    |                  |
| 8           | size in bytes of content (= n)                          | 0x000000000000000a (10)            |                  |
| n           | the FlowFile attributes as utf-8 encoded JSON           | 0x7b2268656c6c6f223a226e696669227d | {"hello":"nifi"} |
| m           | the FlowFile content                                    | 0x48656c6c6f2054435021             | Hello TCP!       |

#### ListenFlowFromTCP

Use this processor to receive TCP packets issued from a `PutFlowToTCP` processor on a different NiFi host.
The packets are decoded and turned back into a FlowFile 
with the same attributes and content of the original FlowFile from the sending NiFi host.

The codec used is described in more detail under "[PutFlowToTCP](#PutFlowToTCP)".

## Attribution

This project originated as a fork from [nerdfunk-net/diode][fork],
mainly to improve test coverage and incorporate some refactorings. 
Huge thanks to the [original author(s)][fork-authors].
Additionally, the original [Apache NiFi][nifi] project has a large influence on the implementation.

## Contributions

Contributions are welcome. 
Please open an issue before working on and creating a pull-request.


[nifi]: https://nifi.apache.org
[fork]: https://github.com/nerdfunk-net/diode
[fork-authors]: https://github.com/nerdfunk-net/diode/graphs/contributors
